package crescoclient.dataplane;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A dataplane message with its verified writer: what a stream opened with envelope frames receives
 * ({@link crescoclient.CrescoClient#get_dataplane(String, crescoclient.core.OnMessageCallback, boolean)}).
 *
 * <p>{@link #writer()} is {@code api:<tenant>/<user>} when an API client wrote it through wsapi, else the bundle or
 * controller that published it as its controller stamped it ({@code plugin:<tenant>/<region>/<agent>/<plugin id>},
 * {@code controller:<tenant>/<region>/<agent>}); null when there is no stamp, or the frame was not an envelope
 * ({@code kind} {@code raw}): treat such a message as unverified. So a client tells a process's real
 * {@code execution_log} (a map written by the executor) from a user's text that looks like one.
 */
public final class Envelope {

    private static final Gson GSON = new Gson();
    private static final Type MAP = new TypeToken<Map<String, Object>>() {}.getType();

    private final String kind, writer, writerRoles, writerTenant, text;
    private final Map<String, Object> properties, map;
    private final byte[] data;

    private Envelope(String kind, String writer, String writerRoles, String writerTenant, Map<String, Object> properties,
                     String text, Map<String, Object> map, byte[] data) {
        this.kind = kind;
        this.writer = writer;
        this.writerRoles = writerRoles;
        this.writerTenant = writerTenant;
        this.properties = properties == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.text = text;
        this.map = map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
        this.data = data;
    }

    /** {@code text}, {@code map}, {@code transfer_end}, {@code bytes}, or {@code raw} (not an envelope). */
    public String kind() { return kind; }
    public String writer() { return writer; }
    public String writerRoles() { return writerRoles; }
    public String writerTenant() { return writerTenant; }
    public Map<String, Object> properties() { return properties; }
    /** The text of a {@code text} (or {@code raw} text) message. */
    public String text() { return text; }
    /** The entries of a {@code map} or {@code transfer_end} message. */
    public Map<String, Object> map() { return map; }
    /** The payload of a {@code bytes} (or {@code raw} binary) message. */
    public byte[] data() { return data == null ? null : data.clone(); }

    /** True when an API client (a person or tool through wsapi), not infrastructure, wrote it. */
    public boolean fromApiUser() { return writer != null && writer.startsWith("api:"); }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    @SuppressWarnings("unchecked")
    private static Envelope of(Map<String, Object> e, String text, Map<String, Object> map, byte[] data) {
        Object p = e.get("properties");
        return new Envelope(str(e.get("kind")), str(e.get("writer")), str(e.get("writer_roles")), str(e.get("writer_tenant")),
                p instanceof Map ? (Map<String, Object>) p : null, text, map, data);
    }

    /** An envelope text frame (text, map or transfer_end); anything else is {@code raw} with no writer. */
    @SuppressWarnings("unchecked")
    public static Envelope parseText(String frame) {
        Map<String, Object> e = null;
        try {
            e = GSON.fromJson(frame, MAP);
        } catch (JsonParseException | IllegalStateException notJson) {
            // raw
        }
        Object kind = e == null ? null : e.get("kind");
        if (!"text".equals(kind) && !"map".equals(kind) && !"transfer_end".equals(kind)) {
            return new Envelope("raw", null, null, null, null, frame, null, null);
        }
        Object m = e.get("map");
        return of(e, str(e.get("text")), m instanceof Map ? (Map<String, Object>) m : null, null);
    }

    /** An envelope binary frame: a 4-byte big-endian header length, the JSON header, the payload. */
    public static Envelope parseBytes(byte[] b, int offset, int length) {
        byte[] frame = Arrays.copyOfRange(b, offset, offset + length);
        if (frame.length >= 4) {
            int n = ByteBuffer.wrap(frame).getInt();
            if (n > 0 && n <= frame.length - 4) {
                try {
                    Map<String, Object> header = GSON.fromJson(new String(frame, 4, n, StandardCharsets.UTF_8), MAP);
                    if (header != null && "bytes".equals(header.get("kind"))) {
                        return of(header, null, null, Arrays.copyOfRange(frame, 4 + n, frame.length));
                    }
                } catch (JsonParseException | IllegalStateException notJson) {
                    // raw
                }
            }
        }
        return new Envelope("raw", null, null, null, null, null, null, frame);
    }

    /** A JSON stream config asking wsapi for envelope frames; IllegalArgumentException for a selector. */
    public static String withEnvelope(String streamConfig) {
        Map<String, Object> cfg = null;
        try {
            cfg = GSON.fromJson(streamConfig, MAP);
        } catch (JsonParseException | IllegalStateException notJson) {
            // a selector
        }
        if (cfg == null) {
            throw new IllegalArgumentException("envelope frames need a JSON stream config ({\"ident_key\": ..., \"ident_id\": ...}), not a selector");
        }
        cfg.put("envelope", "true");
        return GSON.toJson(cfg);
    }

    /** Whether a stream config asks for envelope frames. */
    static boolean requested(String streamConfig) {
        try {
            Map<String, Object> cfg = GSON.fromJson(streamConfig, MAP);
            return cfg != null && "true".equalsIgnoreCase(String.valueOf(cfg.get("envelope")));
        } catch (RuntimeException notJson) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "Envelope[" + kind + " by " + writer + (writerRoles != null ? " (" + writerRoles + ")" : "") + "]";
    }
}
