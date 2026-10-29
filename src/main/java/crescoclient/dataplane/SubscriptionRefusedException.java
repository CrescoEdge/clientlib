package crescoclient.dataplane;

/**
 * The broker refused this dataplane stream's subscription (wsapi activation status 40): a stream another tenant owns, or
 * one bound to another recipient with {@code cresco_to}. The stream stays open for sending.
 */
public class SubscriptionRefusedException extends RuntimeException {

    private final String stream;
    private final String reason;

    public SubscriptionRefusedException(String stream, String reason) {
        super("subscription to dataplane stream '" + stream + "' refused: " + reason);
        this.stream = stream;
        this.reason = reason;
    }

    /** The stream handshake (ident or query) whose subscription was refused. */
    public String getStream() { return stream; }

    /** The broker's reason, as wsapi reported it. */
    public String getReason() { return reason; }
}
