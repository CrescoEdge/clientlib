package crescoclient.tls;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A local wss server standing in for wsapi. Records every HTTP upgrade request (its headers) that reaches it: a client
 * that refused the certificate must leave this empty, which proves no credential was sent. /api/apisocket echoes;
 * /api/dataplane answers the stream handshake; /api/logstreamer sends its activation status. {@code rejectStatus}
 * answers every upgrade with that HTTP status instead.
 */
final class TestServer implements AutoCloseable {

    /** A keytool-made key and certificate. */
    static final class Cert {
        final Path p12;
        final Path pem;
        final X509Certificate cert;
        final KeyStore store;

        Cert(Path p12, Path pem, X509Certificate cert, KeyStore store) {
            this.p12 = p12;
            this.pem = pem;
            this.cert = cert;
            this.store = store;
        }
    }

    static final char[] PW = "changeit".toCharArray();

    /** EC P-384 self-signed, DN O=region-a, OU=agent-a, CN=plugin-7, the given SAN (e.g. dns:localhost,ip:127.0.0.1). */
    static Cert make(String dn, String san) throws Exception {
        Path dir = Files.createTempDirectory("clientlib-tls");
        Path p12 = dir.resolve("s.p12"), pem = dir.resolve("ws.crt.pem");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        List<String> cmd = new ArrayList<>(List.of(keytool, "-genkeypair", "-alias", "wsapi", "-keyalg", "EC", "-groupname", "secp384r1",
                "-sigalg", "SHA384withECDSA", "-dname", dn, "-validity", "2", "-storetype", "PKCS12",
                "-keystore", p12.toString(), "-storepass", "changeit", "-keypass", "changeit"));
        if (san != null) { cmd.add("-ext"); cmd.add("SAN=" + san); }
        run(cmd);
        run(List.of(keytool, "-exportcert", "-rfc", "-alias", "wsapi", "-keystore", p12.toString(), "-storepass", "changeit", "-file", pem.toString()));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(p12.toFile())) { ks.load(in, PW); }
        return new Cert(p12, pem, (X509Certificate) ks.getCertificate("wsapi"), ks);
    }

    private static void run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException("keytool failed: " + out);
    }

    static final io.netty.util.AttributeKey<String> PATH = io.netty.util.AttributeKey.valueOf("path");
    final List<Map<String, String>> requests = Collections.synchronizedList(new ArrayList<>());
    final int port;
    private final EventLoopGroup group;
    private final Channel channel;

    TestServer(Cert cert, int rejectStatus) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(cert.store, PW);
        SslContext ssl = SslContextBuilder.forServer(kmf).build();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        channel = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(ssl.newHandler(ch.alloc()), new HttpServerCodec(), new HttpObjectAggregator(65536),
                                new Recorder(rejectStatus),
                                new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
                                        .websocketPath("/api").checkStartsWith(true).build()),
                                new Endpoint());
                    }
                }).bind("127.0.0.1", 0).sync().channel();
        port = ((InetSocketAddress) channel.localAddress()).getPort();
    }

    private final class Recorder extends ChannelInboundHandlerAdapter {
        private final int reject;
        private String path;

        Recorder(int reject) { this.reject = reject; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest req = (FullHttpRequest) msg;
                Map<String, String> h = new HashMap<>();
                req.headers().forEach(e -> h.put(e.getKey().toLowerCase(), e.getValue()));
                h.put(":path", req.uri());
                requests.add(h);
                path = req.uri();
                ctx.channel().attr(PATH).set(path);
                if (reject > 0) {
                    req.release();
                    DefaultFullHttpResponse r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(reject, "server says no"),
                            Unpooled.copiedBuffer("refused", StandardCharsets.UTF_8));
                    r.headers().set("Content-Length", r.content().readableBytes());
                    ctx.writeAndFlush(r).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete && "/api/logstreamer".equals(path)) {
                ctx.writeAndFlush(new TextWebSocketFrame("{\"status_code\":\"10\",\"status_desc\":\"Listener Active\"}"));
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    private final class Endpoint extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private boolean handshaken;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            String path = ctx.channel().attr(PATH).get();
            if ("/api/dataplane".equals(path) && !handshaken) {
                handshaken = true;
                ctx.writeAndFlush(new TextWebSocketFrame("{\"status_code\":\"10\",\"status_desc\":\"Listener Active\"}"));
                return;
            }
            ctx.writeAndFlush(new TextWebSocketFrame(frame.text()));
        }
    }

    @Override
    public void close() {
        channel.close();
        group.shutdownGracefully();
    }
}
