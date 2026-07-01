package crescoclient.core;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Programmatic jakarta.websocket client endpoint. Registers whole-message handlers for
 * text and binary frames on open and dispatches everything to the supplied {@link WSCallback}.
 */
public class WSocketImp extends Endpoint
{
    private static final Logger LOG = LoggerFactory.getLogger(WSocketImp.class);
    private final WSCallback wSStatusCallback;

    public WSocketImp(WSCallback wSStatusCallback) {
        this.wSStatusCallback = wSStatusCallback;
    }

    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        LOG.debug("onOpen({})", session.getId());

        session.addMessageHandler(String.class, new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String msg) {
                LOG.debug("onMessage(text)");
                wSStatusCallback.onMessage(session, msg);
            }
        });

        session.addMessageHandler(ByteBuffer.class, new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer buffer) {
                LOG.debug("onMessage(bytes)");
                byte[] b = new byte[buffer.remaining()];
                buffer.get(b);
                wSStatusCallback.onMessage(b, 0, b.length);
            }
        });

        wSStatusCallback.onConnect(session);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        int code = closeReason.getCloseCode().getCode();
        String reason = closeReason.getReasonPhrase();
        LOG.debug("onClose({}, {})", code, reason);
        wSStatusCallback.onClose(code, reason);
    }

    @Override
    public void onError(Session session, Throwable cause) {
        LOG.warn("onError: {}", cause == null ? "null" : cause.getMessage());
        wSStatusCallback.onError(cause);
    }
}
