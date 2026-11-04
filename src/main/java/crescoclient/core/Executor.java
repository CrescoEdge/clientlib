package crescoclient.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Client-side abstraction of the <a href="https://github.com/CrescoEdge/executor">io.cresco.executor</a> plugin: run
 * an OS process on an agent and drive its stdin.
 *
 * <p>A runner is registered with {@link #config_process} and started with {@link #run_process}; its stdout and stderr
 * stream over the dataplane under its {@code stream_name} ({@code type} {@code output} / {@code error}), and its exit
 * arrives there as an {@code execution_log} event.
 *
 * <p><b>Stdin is never the dataplane.</b> The executor keeps a process's stdin open only when {@code config_process}
 * asked for it ({@code stdin=true}, or an {@code -interactive-} command), and takes input only from
 * {@link #write_stdin}, a MsgEvent action it accepts from the caller that started the runner, an admin or infra. One
 * write carries at most {@link #MAX_STDIN_WRITE} bytes; larger input goes in several writes, in order. wsapi refuses a
 * dataplane stream that would write stdin ({@code input_id} {@code input} / {@code input-*}), and so does
 * {@link crescoclient.dataplane.DataPlaneInterface}.
 *
 * <pre>{@code
 * client.executor.config_process("global-region", "agent-a", "job-1", "sort", true, false, null);
 * client.executor.run_process("global-region", "agent-a", "job-1", null);
 * client.executor.write_stdin("global-region", "agent-a", "job-1", "pear");
 * client.executor.write_stdin("global-region", "agent-a", "job-1", "apple", true, true, null);   // and end input
 * }</pre>
 *
 * <p>Mirrors the pycrescolib {@code executor} submodule method for method. A {@code plugin_id} of {@code null} is
 * resolved by name through {@link GlobalController#find_plugin}.
 */
public class Executor {

    /** Bundle-SymbolicName of the executor plugin. */
    public static final String EXECUTOR_PLUGIN_NAME = "io.cresco.executor";
    /** The executor's bound on one write_stdin. */
    public static final int MAX_STDIN_WRITE = 64 * 1024;
    /** write_stdin status: written ("9" refused, "8" bad params, "7" not written). */
    public static final String STATUS_WRITTEN = "10";

    /** write_stdin was not accepted. */
    public static class StdinWriteException extends RuntimeException {
        private final String streamName, status, refusal, statusDesc;
        private final long written;

        public StdinWriteException(String streamName, String status, String refusal, String statusDesc, long written) {
            super("write_stdin to '" + streamName + "' failed: "
                    + (refusal != null ? refusal + (statusDesc != null ? " (" + statusDesc + ")" : "")
                    : statusDesc != null ? statusDesc : status == null ? "no reply" : "status " + status)
                    + (written > 0 ? "; " + written + " byte(s) were written before" : ""));
            this.streamName = streamName;
            this.status = status;
            this.refusal = refusal;
            this.statusDesc = statusDesc;
            this.written = written;
        }

        public String getStreamName() { return streamName; }
        /** The reply's status, or null when no reply arrived. */
        public String getStatus() { return status; }
        /** {@code not-owner}, {@code no-stdin} or {@code not-running} when the executor refused, else null. */
        public String getRefusal() { return refusal; }
        public String getStatusDesc() { return statusDesc; }
        /** Bytes earlier writes of the same call delivered. */
        public long getWritten() { return written; }
    }

    private final Messaging messaging;
    private final GlobalController globalcontroller;

    /** {@link crescoclient.CrescoClient} builds this for you as {@code client.executor}. */
    public Executor(Messaging messaging, GlobalController globalcontroller) {
        this.messaging = messaging;
        this.globalcontroller = globalcontroller;
    }

    /** The executor plugin_id on an agent, or null when none is loaded. */
    public String find_plugin(String region, String agent) {
        return globalcontroller.find_plugin(region, agent, EXECUTOR_PLUGIN_NAME);
    }

    private String plugin(String region, String agent, String plugin_id) {
        String pid = plugin_id != null ? plugin_id : find_plugin(region, agent);
        if (pid == null) throw new NoSuchElementException("no " + EXECUTOR_PLUGIN_NAME + " plugin on " + region + "/" + agent);
        return pid;
    }

    private Map<String, String> send(String type, Map<String, Object> payload, String region, String agent, String pid) {
        Map<String, String> r = messaging.global_plugin_msgevent(true, type, payload, region, agent, pid);
        return r == null ? new HashMap<>() : r;
    }

    private static Map<String, Object> payload(String action, String stream_name) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("action", action);
        p.put("stream_name", stream_name);
        return p;
    }

    /**
     * Register a runner (infra / admin).
     *
     * @param stdin keep the process's stdin open for {@link #write_stdin}
     * @return {@code config_status} and {@code stdin} (Boolean: whether the runner takes write_stdin) and
     *         {@code error_msg} (String or null)
     */
    public Map<String, Object> config_process(String region, String agent, String stream_name, String command, boolean stdin,
                                              boolean metrics, String plugin_id) {
        Map<String, Object> p = payload("config_process", stream_name);
        p.put("command", command);
        p.put("stdin", Boolean.toString(stdin));
        p.put("metrics", Boolean.toString(metrics));
        Map<String, String> r = send("CONFIG", p, region, agent, plugin(region, agent, plugin_id));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("config_status", "true".equalsIgnoreCase(r.get("config_status")));
        out.put("stdin", "true".equalsIgnoreCase(r.get("stdin")));
        out.put("error_msg", r.get("error_msg"));
        return out;
    }

    /** Start a configured runner (infra / admin); the caller that starts it may write its stdin. */
    public boolean run_process(String region, String agent, String stream_name, String plugin_id) {
        return "true".equalsIgnoreCase(send("EXEC", payload("run_process", stream_name), region, agent,
                plugin(region, agent, plugin_id)).get("status"));
    }

    /** True while the runner's process is running. */
    public boolean status_process(String region, String agent, String stream_name, String plugin_id) {
        return "true".equalsIgnoreCase(send("CONFIG", payload("status_process", stream_name), region, agent,
                plugin(region, agent, plugin_id)).get("run_status"));
    }

    /** Kill the runner's process (its whole tree) and free the runner (infra / admin). */
    public boolean end_process(String region, String agent, String stream_name, String plugin_id) {
        return "true".equalsIgnoreCase(send("CONFIG", payload("end_process", stream_name), region, agent,
                plugin(region, agent, plugin_id)).get("end_status"));
    }

    /** Write a line of text (UTF-8, then a newline) to a running process's stdin. */
    public long write_stdin(String region, String agent, String stream_name, String line) {
        return write_stdin(region, agent, stream_name, line, true, false, null);
    }

    /**
     * Write text to a running process's stdin.
     *
     * @param data    UTF-8 text, or null to write nothing (with {@code close}, just end input)
     * @param newline append a newline
     * @param close   close stdin after the data (end of input)
     * @return the bytes written
     * @throws StdinWriteException the executor refused or failed a write
     * @throws NoSuchElementException no executor plugin on the agent (plugin_id null)
     */
    public long write_stdin(String region, String agent, String stream_name, String data, boolean newline, boolean close,
                            String plugin_id) {
        byte[] raw = data == null ? new byte[0] : (newline ? data + "\n" : data).getBytes(StandardCharsets.UTF_8);
        return write_stdin(region, agent, stream_name, raw, close, plugin_id);
    }

    /**
     * Write bytes to a running process's stdin, as they are. Input over {@link #MAX_STDIN_WRITE} bytes goes in several
     * writes, in order; {@code close} applies after the last.
     *
     * @return the bytes written
     * @throws StdinWriteException the executor refused or failed a write ({@link StdinWriteException#getWritten()}
     *         says how much of the input earlier writes delivered)
     */
    public long write_stdin(String region, String agent, String stream_name, byte[] data, boolean close, String plugin_id) {
        byte[] raw = data == null ? new byte[0] : data;
        if (raw.length == 0 && !close) return 0;
        String pid = plugin(region, agent, plugin_id);
        long written = 0;
        int off = 0;
        do {
            int end = Math.min(raw.length, off + MAX_STDIN_WRITE);
            byte[] chunk = Arrays.copyOfRange(raw, off, end);
            boolean last = end == raw.length;
            Map<String, Object> p = payload("write_stdin", stream_name);
            if (chunk.length > 0) p.put("data_b64", Base64.getEncoder().encodeToString(chunk));
            if (close && last) p.put("close_stdin", "true");
            Map<String, String> r = messaging.global_plugin_msgevent(true, "EXEC", p, region, agent, pid);
            if (r == null || !STATUS_WRITTEN.equals(r.get("status"))) {
                throw new StdinWriteException(stream_name, r == null ? null : r.get("status"), r == null ? null : r.get("refusal"),
                        r == null ? null : r.get("status_desc"), written);
            }
            written += chunk.length;
            off = end;
        } while (off < raw.length);
        return written;
    }

    /** Close a running process's stdin (end of input). */
    public void close_stdin(String region, String agent, String stream_name, String plugin_id) {
        write_stdin(region, agent, stream_name, new byte[0], true, plugin_id);
    }
}
