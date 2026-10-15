package crescoclient.conformance;

import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression (2026-10-15): a dataplane stream received its own message TWICE for one send.
 *
 * <p>The transport pooled connections per thread, so {@code send()} from any thread other than the
 * one that connected opened a second WebSocket; its onConnect re-sent the stream handshake, the
 * server registered a second subscription for the same stream, and every message on the stream
 * reached the callback once per socket. This server mimics wsapi's /api/dataplane (first text frame
 * is the handshake and subscribes that connection; later text frames are published to every
 * subscribed connection, the sender included, which is the dataplane's topic semantics) and counts
 * connections and handshakes.
 */
public class DataPlaneSessionTest {

    private static EventLoopGroup group;
    private static Channel server;
    private static int port;
    private static final AtomicInteger connections = new AtomicInteger();
    private static final AtomicInteger handshakes = new AtomicInteger();
    private static final Set<Channel> subscribers = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void startServer() throws Exception {
        // self-signed server key, generated with the JDK's keytool (no BouncyCastle on this classpath)
        Path dir = Files.createTempDirectory("dpsession");
        Path ks = dir.resolve("server.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "s", "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=plugin,OU=agent,O=region", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", ks.toString(), "-storepass", "changeit", "-keypass", "changeit")
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertEquals(0, p.waitFor(), "keytool failed");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(ks.toFile())) { store.load(in, "changeit".toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, "changeit".toCharArray());
        SslContext ssl = SslContextBuilder.forServer(kmf).build();

        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        connections.incrementAndGet();
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc()), new HttpServerCodec(),
                                new HttpObjectAggregator(65536),
                                new WebSocketServerProtocolHandler("/api/dataplane"),
                                new DataPlaneEmulator());
                    }
                }).bind("127.0.0.1", 0).sync().channel();
        port = ((InetSocketAddress) server.localAddress()).getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.close();
        if (group != null) group.shutdownGracefully();
    }

    static class DataPlaneEmulator extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            if (subscribers.add(ctx.channel())) {          // first frame: the stream handshake
                handshakes.incrementAndGet();
                ctx.writeAndFlush(new TextWebSocketFrame("{\"status_code\":\"10\",\"status_desc\":\"Listener Active\"}"));
                return;
            }
            String text = frame.text();                     // data: published to every subscriber
            for (Channel c : subscribers) {
                if (c.isActive()) c.writeAndFlush(new TextWebSocketFrame(text));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            subscribers.remove(ctx.channel());
            super.channelInactive(ctx);
        }
    }

    @Test
    void oneSendFromAnyThreadIsDeliveredOnceOnOneConnection() throws Exception {
        List<String> got = Collections.synchronizedList(new ArrayList<>());
        String cfg = "{\"ident_key\":\"stream_name\",\"ident_id\":\"dup\",\"io_type_key\":\"type\",\"input_id\":\"in\"}";
        DataPlaneInterface dp = new DataPlaneInterface("127.0.0.1", port, "k", cfg, new OnMessageCallback() {
            @Override public void onMessage(String msg) { got.add(msg); }
            @Override public void onMessage(byte[] b, int offset, int length) { }
        });
        try {
            dp.connect();                                   // connects on a background thread
            long deadline = System.currentTimeMillis() + 15_000;
            while (!(dp.connected() && handshakes.get() == 1) && System.currentTimeMillis() < deadline) Thread.sleep(20);
            assertTrue(dp.connected(), "dataplane did not connect");

            dp.send("hello-main");                          // this test thread
            Thread other = new Thread(() -> dp.send("hello-other"));
            other.start();
            other.join();

            deadline = System.currentTimeMillis() + 5_000;
            while (got.size() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20);
            Thread.sleep(300);                              // a duplicate would arrive right behind

            assertEquals(1, connections.get(), "sends must reuse the stream's connection");
            assertEquals(1, handshakes.get(), "the stream handshake must be sent once");
            assertEquals(1, Collections.frequency(got, "hello-main"), "got " + got);
            assertEquals(1, Collections.frequency(got, "hello-other"), "got " + got);
            assertEquals(2, got.size(), "got " + got);
        } finally {
            dp.close();
        }
    }
}
