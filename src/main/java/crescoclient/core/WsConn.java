package crescoclient.core;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.nio.ByteBuffer;

/**
 * Thin wrapper over a Netty {@link Channel} that gives the Cresco client interfaces the small
 * send API they used to call on a jakarta {@code Session} ({@code getBasicRemote().sendText/
 * sendBinary}). Binary sends copy the caller's buffer (the old blocking send consumed it
 * synchronously; Netty writes are async) and pace on channel writability so a fast producer
 * doesn't outrun the socket.
 */
public class WsConn {

    private final Channel channel;

    public WsConn(Channel channel) { this.channel = channel; }

    public void sendText(String s) {
        channel.writeAndFlush(new TextWebSocketFrame(s));
    }

    public void sendBinary(ByteBuffer bb) {
        pace();
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(bb)));
    }

    public void sendBinary(ByteBuffer bb, boolean complete) {
        pace();
        channel.writeAndFlush(new BinaryWebSocketFrame(complete, 0, Unpooled.copiedBuffer(bb)));
    }

    // Backpressure: block briefly while the channel's write buffer is over the high watermark,
    // matching the pacing the old blocking getBasicRemote().sendBinary() provided.
    private void pace() {
        Channel ch = channel;
        while (ch.isActive() && !ch.isWritable()) {
            try { Thread.sleep(0, 200_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    public boolean isOpen() { return channel != null && channel.isActive(); }

    public void close() { if (channel != null) channel.close(); }

    public Channel channel() { return channel; }
}
