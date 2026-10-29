package crescoclient.core;

import com.google.common.primitives.Bytes;

import java.io.InputStream;

/**
 * Receives messages from a dataplane or log stream: text frames via {@code onMessage(String)} and binary
 * frames via {@code onMessage(byte[], int, int)}.
 */
public interface OnMessageCallback {

    void onMessage(String msg);
    void onMessage(byte[] b, int offset, int length);


    /**
     * The broker refused this dataplane stream's subscription (wsapi status 40); the stream stays open for sending.
     * Called once per session. The default does nothing.
     *
     * @param reason the broker's reason
     */
    default void onSubscriptionRefused(String reason) { }
}
