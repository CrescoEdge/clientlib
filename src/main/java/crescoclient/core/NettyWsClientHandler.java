package crescoclient.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.concurrent.CountDownLatch;

/**
 * Netty inbound handler for the Cresco WebSocket client. Fires the handshake latch and the
 * {@link WSCallback} lifecycle/message events (whole-message frames only — a WebSocketFrameAggregator
 * sits in front). Callbacks receive a {@link WsConn} wrapping this channel.
 */
public class NettyWsClientHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final WSCallback callback;
    private final CountDownLatch handshakeLatch;
    private volatile boolean handshakeDone = false;

    public NettyWsClientHandler(WSCallback callback, CountDownLatch handshakeLatch) {
        this.callback = callback;
        this.handshakeLatch = handshakeLatch;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketClientProtocolHandler.ClientHandshakeStateEvent) {
            if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                handshakeDone = true;
                handshakeLatch.countDown();
                try { callback.onConnect(new WsConn(ctx.channel())); } catch (Exception ignore) {}
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            if (frame instanceof TextWebSocketFrame) {
                callback.onMessage(new WsConn(ctx.channel()), ((TextWebSocketFrame) frame).text());
            } else if (frame instanceof BinaryWebSocketFrame) {
                io.netty.buffer.ByteBuf buf = frame.content();
                byte[] b = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), b);
                callback.onMessage(b, 0, b.length);
            }
        } catch (Exception ex) {
            callback.onError(ex);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // release any waiter if the connection died before handshake
        if (!handshakeDone) handshakeLatch.countDown();
        try { callback.onClose(1006, "channel inactive"); } catch (Exception ignore) {}
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try { callback.onError(cause); } catch (Exception ignore) {}
        ctx.close();
    }
}
