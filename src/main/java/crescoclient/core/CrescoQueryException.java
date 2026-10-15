package crescoclient.core;

import java.util.Map;

/**
 * An inventory/lookup query could not be answered: "couldn't ask", as opposed to "asked, and the
 * answer is empty".
 *
 * <p>Thrown by the listing methods ({@link GlobalController#list_plugins}, {@link GlobalController#find_plugin},
 * {@link GlobalController#get_agent_list}, {@link GlobalController#get_region_list},
 * {@link GlobalController#get_pipeline_list}, {@link GlobalController#get_pipeline_id_by_name},
 * {@link Agents#list_plugin_agent}, {@link Agents#find_plugin}) when no reply arrived (RPC timeout, e.g.
 * right after a global restart), the reply is an error (wsapi's {@code status} 504 "no reply within ...",
 * an agent's {@code status_code} 9), or it lacks the list a successful reply always carries. Callers that
 * act on "absent" (deploy a plugin when it is not loaded) must not treat it as empty. Unchecked so that
 * existing callers keep compiling; before it existed these methods returned null/empty on failure.
 */
public class CrescoQueryException extends RuntimeException {

    public CrescoQueryException(String message) {
        super(message);
    }

    /**
     * The value of {@code field} in a listing reply, or throw when the query was not answered.
     *
     * @param reply the RPC reply params (null when no reply arrived in time)
     * @param field the field every successful reply carries (the controller sets it even for an empty list)
     * @param what  the query, for the message
     * @return the (non-null) field value
     */
    static String requireField(Map<String, String> reply, String field, String what) {
        if (reply == null) {
            throw new CrescoQueryException(what + ": no reply (RPC timed out or the connection failed)");
        }
        String v = reply.get(field);
        if (v != null) return v;
        String reason = reply.get("error") != null ? reply.get("error")
                : reply.get("status_desc") != null ? reply.get("status_desc") : "reply has no '" + field + "'";
        String status = reply.get("status") != null ? reply.get("status") : reply.get("status_code");
        throw new CrescoQueryException(what + ": " + reason + (status != null ? " (status " + status + ")" : ""));
    }

    /** {@code decoded}, or throw when a compressed list could not be decoded. */
    static <T> T requireDecoded(T decoded, String what) {
        if (decoded == null) throw new CrescoQueryException(what + ": undecodable reply");
        return decoded;
    }
}
