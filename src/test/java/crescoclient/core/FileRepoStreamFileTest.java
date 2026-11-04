package crescoclient.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileRepo.stream_file commits a file only when the streamfile terminal frame's SHA-384, byte count and chunk count all
 * match what arrived (wsapi framing: binary [transfer_id][6-digit seq][payload] chunks, then a JSON text terminal frame).
 */
class FileRepoStreamFileTest {

    static final byte[] DATA = new byte[4099];
    static { new Random(7).nextBytes(DATA); }

    static String sha384(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(b));
    }

    /** Plays filerepo + wsapi: on the streamfile RPC, the chunks then the terminal frame, on a background thread. */
    static final class Fake implements FileRepo.Transport {
        final int chunk;
        UnaryOperator<List<byte[]>> mutate = UnaryOperator.identity();
        Map<String, String> terminalExtra = new HashMap<>();
        Map<String, String> reply = new HashMap<>(Map.of("status", "10", "status_desc", "transfer accepted"));
        boolean noTerminal;
        OnMessageCallback cb;
        final List<Map<String, Object>> calls = Collections.synchronizedList(new ArrayList<>());
        final List<String> closed = new ArrayList<>();

        Fake(int chunk) { this.chunk = chunk; }

        @Override public String openStream(String selector, OnMessageCallback callback, long t) { cb = callback; return null; }
        @Override public void closeStream(String selector) { closed.add(selector); }

        @Override
        public Map<String, String> rpc(boolean isRpc, Map<String, Object> payload, String r, String a, String p) {
            calls.add(new HashMap<>(payload));
            if ("streamfile".equals(payload.get("action")) && "10".equals(reply.get("status"))) {
                new Thread(() -> play(payload)).start();
            }
            return reply;
        }

        void play(Map<String, Object> req) {
            String tid = (String) req.get("transfer_id");
            int start = Integer.parseInt((String) req.get("start_byte")), len = Integer.parseInt((String) req.get("byte_length"));
            byte[] body = java.util.Arrays.copyOfRange(DATA, start, start + len);
            List<byte[]> frames = new ArrayList<>();
            for (int i = 0, n = 0; i < body.length; i += chunk, n++) {
                byte[] head = (tid + String.format("%06d", n)).getBytes(StandardCharsets.US_ASCII);
                byte[] payload = java.util.Arrays.copyOfRange(body, i, Math.min(body.length, i + chunk));
                byte[] f = new byte[head.length + payload.length];
                System.arraycopy(head, 0, f, 0, head.length);
                System.arraycopy(payload, 0, f, head.length, payload.length);
                frames.add(f);
            }
            int chunks = frames.size();
            frames = mutate.apply(frames);
            byte[] other = "other-transfer000000junk".getBytes(StandardCharsets.US_ASCII);
            cb.onMessage(other, 0, other.length);                  // another transfer's chunk: ignored
            for (byte[] f : frames) cb.onMessage(f, 0, f.length);
            if (noTerminal) return;
            Map<String, String> t = new HashMap<>();
            t.put("transfer_id", tid);
            t.put("transfer_end", "true");
            t.put("transfer_status", "10");
            t.put("transfer_desc", "complete");
            t.put("total_bytes", String.valueOf(body.length));
            t.put("seq_num", String.valueOf(chunks));
            try { t.put("sha384", sha384(body)); } catch (Exception e) { throw new RuntimeException(e); }
            t.putAll(terminalExtra);
            cb.onMessage(new com.google.gson.Gson().toJson(t));
        }
    }

    static List<String> files(Path dir) throws Exception {
        try (var s = Files.list(dir)) { return s.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    @Test
    void aVerifiedTransferIsWritten(@TempDir Path dir) throws Exception {
        Fake f = new Fake(1000);
        Path out = dir.resolve("f.bin");
        Map<String, String> r = new FileRepo(f).stream_file("r", "a", "p", "/repo/f.bin", out, DATA.length, 0, null, 10_000);
        assertArrayEquals(DATA, Files.readAllBytes(out));
        assertEquals(sha384(DATA), r.get("sha384"));
        assertEquals(String.valueOf(DATA.length), r.get("total_bytes"));
        Map<String, Object> req = f.calls.get(0);
        assertEquals("streamfile", req.get("action"));
        assertEquals("stream_name", req.get("ident_key"));
        assertEquals(r.get("transfer_id"), req.get("transfer_id"));
        assertEquals(1, f.closed.size());
        assertEquals(List.of("f.bin"), files(dir), "no staging file left");
    }

    @Test
    void aByteRange(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("part");
        new FileRepo(new Fake(7)).stream_file("r", "a", "p", "/f", out, 100, 50, null, 10_000);
        assertArrayEquals(java.util.Arrays.copyOfRange(DATA, 50, 150), Files.readAllBytes(out));
    }

    @Test
    void anUnverifiableTransferWritesNothing(@TempDir Path dir) throws Exception {
        List<java.util.function.Consumer<Fake>> cases = List.of(
                x -> x.terminalExtra.put("sha384", "0".repeat(96)),
                x -> x.terminalExtra.put("total_bytes", "5"),
                x -> x.mutate = fr -> { List<byte[]> l = new ArrayList<>(fr); l.remove(2); return l; },
                x -> x.mutate = fr -> { List<byte[]> l = new ArrayList<>(fr); l.add(fr.get(fr.size() - 1)); return l; },
                x -> { x.terminalExtra.put("transfer_status", "9"); x.terminalExtra.put("transfer_desc", "cancelled"); },
                x -> x.terminalExtra.put("sha384", ""));
        for (int i = 0; i < cases.size(); i++) {
            Fake f = new Fake(500);
            cases.get(i).accept(f);
            Path sub = Files.createDirectories(dir.resolve("c" + i));
            assertThrows(FileRepo.StreamFileException.class,
                    () -> new FileRepo(f).stream_file("r", "a", "p", "/f", sub.resolve("f.bin"), DATA.length, 0, null, 10_000), "case " + i);
            assertEquals(List.of(), files(sub), "case " + i + ": nothing written");
        }
    }

    @Test
    void aRefusedRequestCarriesItsStatus(@TempDir Path dir) throws Exception {
        Fake f = new Fake(100);
        f.reply = Map.of("status", "8", "status_desc", "invalid byte range");
        FileRepo.StreamFileException e = assertThrows(FileRepo.StreamFileException.class,
                () -> new FileRepo(f).stream_file("r", "a", "p", "/f", dir.resolve("x"), 10, 0, null, 10_000));
        assertEquals("8", e.status());
        assertTrue(e.getMessage().contains("invalid byte range"));
        assertEquals(List.of(), files(dir));
    }

    @Test
    void noTerminalFrameTimesOutAndCancels(@TempDir Path dir) throws Exception {
        Fake f = new Fake(100);
        f.noTerminal = true;
        assertThrows(FileRepo.StreamFileException.class,
                () -> new FileRepo(f).stream_file("r", "a", "p", "/f", dir.resolve("x"), DATA.length, 0, null, 1_000));
        assertEquals("streamfilecancel", f.calls.get(f.calls.size() - 1).get("action"));
        assertEquals(List.of(), files(dir));
    }
}
