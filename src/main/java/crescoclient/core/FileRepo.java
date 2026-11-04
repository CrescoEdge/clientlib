package crescoclient.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.CrescoClient;
import crescoclient.dataplane.DataPlaneInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * File transfers from a filerepo plugin over the dataplane ({@code streamfile}), verified. A submodule of
 * {@link CrescoClient} ({@code client.filerepo}); the Python client's {@code filerepo} is identical.
 *
 * <p>filerepo sends a byte range of a cataloged file as dataplane chunks, then a terminal frame with the transfer's
 * status and the SHA-384 of the bytes it sent. Through wsapi a chunk is a binary frame
 * {@code [transfer_id][seq_num, 6 digits][payload]} and the terminal frame a JSON text frame
 * {@code {"transfer_id", "transfer_end": "true", "transfer_status", "total_bytes", "sha384", ...}}.
 * {@link #stream_file} reassembles the chunks in order into a private staging file next to the destination and moves
 * it into place only when every chunk arrived, the byte count is right and the SHA-384 matches; otherwise it throws
 * {@link StreamFileException} and leaves no file behind. {@code streamfile} needs the {@code infra} or {@code admin}
 * role on the filerepo agent.
 */
public class FileRepo {

    private static final Logger LOG = LoggerFactory.getLogger(FileRepo.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    static final int MAX_OUT_OF_ORDER = 64;

    /** A streamfile transfer that failed or could not be verified; {@link #status()} is filerepo's, when it gave one. */
    public static class StreamFileException extends IOException {
        private final String status;

        public StreamFileException(String message, String status) {
            super(message);
            this.status = status;
        }

        public String status() { return status; }
    }

    /** What stream_file needs from a client (the CrescoClient, or a stand-in in tests). */
    public interface Transport {
        /** Open a receive-only dataplane stream for {@code selector}; null once it receives, else why not. */
        String openStream(String selector, OnMessageCallback callback, long timeoutMs) throws Exception;
        void closeStream(String selector);
        Map<String, String> rpc(boolean isRpc, Map<String, Object> payload, String region, String agent, String plugin);
    }

    private final Transport transport;

    public FileRepo(CrescoClient client) {
        this(new Transport() {
            @Override
            public String openStream(String selector, OnMessageCallback cb, long timeoutMs) throws Exception {
                DataPlaneInterface dp = client.get_dataplane(selector, cb);
                dp.connect();
                return dp.awaitActive(timeoutMs) && dp.isReceiving() ? null : String.valueOf(dp.last_error());
            }
            @Override public void closeStream(String selector) { client.close_dataplane(selector); }
            @Override public Map<String, String> rpc(boolean isRpc, Map<String, Object> payload, String r, String a, String p) {
                return client.messaging.global_plugin_msgevent(isRpc, "EXEC", payload, r, a, p);
            }
        });
    }

    public FileRepo(Transport transport) {
        this.transport = transport;
    }

    /** Reassembles one transfer's chunks, in order, into the staging file; remembers its terminal frame. */
    static final class Receiver implements OnMessageCallback {
        final String tid;
        final byte[] prefix;
        final FileChannel out;
        final MessageDigest sha;
        final TreeMap<Integer, byte[]> pending = new TreeMap<>();
        final CountDownLatch done = new CountDownLatch(1);
        long written;
        int nextSeq;
        Map<String, String> terminal;
        String error;

        Receiver(String tid, FileChannel out) throws Exception {
            this.tid = tid;
            this.prefix = tid.getBytes(StandardCharsets.US_ASCII);
            this.out = out;
            this.sha = MessageDigest.getInstance("SHA-384");
        }

        @Override
        public synchronized void onMessage(byte[] b, int offset, int length) {
            if (terminal != null || error != null || length < prefix.length + 6) return;
            for (int i = 0; i < prefix.length; i++) if (b[offset + i] != prefix[i]) return;   // another transfer's chunk
            int seq;
            try {
                seq = Integer.parseInt(new String(b, offset + prefix.length, 6, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                return;
            }
            if (seq < nextSeq || pending.containsKey(seq)) {
                fail("chunk " + seq + " arrived twice");
                return;
            }
            int start = offset + prefix.length + 6;
            byte[] payload = java.util.Arrays.copyOfRange(b, start, offset + length);
            pending.put(seq, payload);
            if (pending.size() > MAX_OUT_OF_ORDER) {
                fail("chunk " + nextSeq + " never arrived (" + pending.size() + " later chunks waiting)");
                return;
            }
            try {
                while (pending.containsKey(nextSeq)) {
                    byte[] d = pending.remove(nextSeq);
                    ByteBuffer bb = ByteBuffer.wrap(d);
                    while (bb.hasRemaining()) out.write(bb);
                    sha.update(d);
                    written += d.length;
                    nextSeq++;
                }
            } catch (IOException e) {
                fail("cannot write the staging file: " + e.getMessage());
            }
        }

        @Override
        public synchronized void onMessage(String msg) {
            Map<String, String> m;
            try {
                m = GSON.fromJson(msg, new TypeToken<Map<String, String>>() {}.getType());
            } catch (Exception notJson) {
                return;
            }
            if (m == null || !"true".equals(m.get("transfer_end")) || !tid.equals(m.get("transfer_id"))) return;
            terminal = m;
            done.countDown();
        }

        private void fail(String why) {
            error = why;
            done.countDown();
        }
    }

    /**
     * Download {@code byte_length} bytes of {@code file_path} (from {@code start_byte}) into {@code dest}, verified.
     *
     * @param dst_region  region of the filerepo plugin
     * @param dst_agent   agent of the filerepo plugin
     * @param dst_plugin  plugin id of the filerepo plugin
     * @param file_path   the file's path on that agent (as its repository lists it)
     * @param dest        where to write it; written only after the transfer is verified
     * @param byte_length how many bytes (the file's size for a whole file)
     * @param start_byte  where to start
     * @param buffer_size chunk size, or null for filerepo's default (capped at 768 KB)
     * @param timeout_ms  how long to wait for the whole transfer
     * @return {@code transfer_id}, {@code total_bytes} and {@code sha384} of the verified transfer
     * @throws StreamFileException refused, cancelled, failed, incomplete, or the SHA-384 did not match; nothing written
     */
    public Map<String, String> stream_file(String dst_region, String dst_agent, String dst_plugin, String file_path, Path dest,
                                           long byte_length, long start_byte, Integer buffer_size, long timeout_ms)
            throws StreamFileException {
        String tid = "jc-" + hex(12);
        String ident = "sf-" + hex(16);
        Path target = dest.toAbsolutePath();
        Path staging = target.resolveSibling("." + target.getFileName() + "." + tid + ".part");
        String selector = "stream_name='" + ident + "'";
        boolean committed = false;
        boolean opened = false;
        try (FileChannel out = FileChannel.open(staging, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            Receiver rx = new Receiver(tid, out);
            opened = true;
            String notOpen = transport.openStream(selector, rx, 15_000);
            if (notOpen != null) {
                throw new StreamFileException("the dataplane stream for the transfer did not open: " + notOpen, null);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "streamfile");
            payload.put("file_path", file_path);
            payload.put("start_byte", String.valueOf(start_byte));
            payload.put("byte_length", String.valueOf(byte_length));
            payload.put("ident_key", "stream_name");
            payload.put("ident_id", ident);
            payload.put("transfer_id", tid);
            if (buffer_size != null) payload.put("buffer_size", String.valueOf(buffer_size));
            Map<String, String> reply = transport.rpc(true, payload, dst_region, dst_agent, dst_plugin);
            String status = reply == null ? null : reply.get("status");
            if (!"10".equals(status)) {
                throw new StreamFileException("streamfile refused (" + (status == null ? "no reply" : status) + "): "
                        + (reply == null ? "" : reply.getOrDefault("status_desc", "")), status);
            }
            if (!rx.done.await(timeout_ms, TimeUnit.MILLISECONDS)) {
                cancel(dst_region, dst_agent, dst_plugin, tid);
                throw new StreamFileException("streamfile " + tid + " did not finish within " + timeout_ms + " ms (cancelled)", null);
            }
            Map<String, String> terminal;
            String error, digest;
            long written;
            int chunks;
            synchronized (rx) {
                terminal = rx.terminal;
                error = rx.error;
                written = rx.written;
                chunks = rx.nextSeq;
                digest = HexFormat.of().formatHex(rx.sha.digest());
            }
            if (error != null) {
                cancel(dst_region, dst_agent, dst_plugin, tid);
                throw new StreamFileException("streamfile " + tid + ": " + error, null);
            }
            String tStatus = terminal.get("transfer_status");
            if (!"10".equals(tStatus)) {
                throw new StreamFileException("streamfile " + tid + " ended with status " + tStatus + ": " + terminal.getOrDefault("transfer_desc", ""), tStatus);
            }
            String wantChunks = terminal.get("seq_num");
            if (wantChunks != null && !wantChunks.equals(String.valueOf(chunks))) {
                throw new StreamFileException("streamfile " + tid + ": " + chunks + " of " + wantChunks + " chunks arrived", null);
            }
            long total = Long.parseLong(terminal.getOrDefault("total_bytes", "-1"));
            if (total != written || written != byte_length) {
                throw new StreamFileException("streamfile " + tid + ": " + written + " bytes arrived, the sender sent " + total
                        + ", " + byte_length + " were asked for", null);
            }
            String want = terminal.getOrDefault("sha384", "").toLowerCase(java.util.Locale.ROOT);
            if (want.isEmpty() || !MessageDigest.isEqual(want.getBytes(StandardCharsets.US_ASCII), digest.getBytes(StandardCharsets.US_ASCII))) {
                throw new StreamFileException("streamfile " + tid + ": the SHA-384 of the received bytes (" + digest
                        + ") is not the sender's (" + (want.isEmpty() ? "none" : want) + ")", null);
            }
            out.force(true);
            out.close();
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
            committed = true;
            Map<String, String> r = new HashMap<>();
            r.put("transfer_id", tid);
            r.put("total_bytes", String.valueOf(written));
            r.put("sha384", digest);
            return r;
        } catch (StreamFileException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamFileException("interrupted", null);
        } catch (Exception e) {
            throw new StreamFileException("streamfile " + tid + ": " + e, null);
        } finally {
            if (!committed) {
                try { Files.deleteIfExists(staging); } catch (IOException ignore) { }
            }
            if (opened) {
                try { transport.closeStream(selector); } catch (Exception ignore) { }
            }
        }
    }

    private void cancel(String region, String agent, String plugin, String tid) {
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("action", "streamfilecancel");
            p.put("transfer_id", tid);
            transport.rpc(false, p, region, agent, plugin);
        } catch (Exception e) {
            LOG.debug("streamfilecancel {} not sent: {}", tid, e.getMessage());
        }
    }

    private static String hex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

}
