package com.github.serezhka.airplay.server.internal;

import com.github.serezhka.airplay.server.AirPlayConfig;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.handler.control.ControlHandler;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class ControlServer implements Runnable {
    
    private static final Logger log = Logger.getLogger(ControlServer.class.getName());

    private final SessionManager sessionManager = new SessionManager();

    private final AirPlayConfig airPlayConfig;
    private final AirPlayConsumer airPlayConsumer;

    private Thread thread;
    private int port;
    
    public ControlServer(AirPlayConfig airPlayConfig, AirPlayConsumer airPlayConsumer) {
        this.airPlayConfig = airPlayConfig;
        this.airPlayConsumer = airPlayConsumer;
    }
    
    public int getPort() {
        return port;
    }

    public void start() throws InterruptedException {
        thread = new Thread(this);
        thread.start();
        synchronized (this) {
            wait();
        }
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void run() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        EventLoopGroup bossGroup = eventLoopGroup();
        EventLoopGroup workerGroup = eventLoopGroup();
        try {
            serverBootstrap
                    .group(bossGroup, workerGroup)
                    .channel(serverSocketChannelClass())
                    .localAddress(new InetSocketAddress(0)) // bind random port
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(final SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new RtspDecoder(),
                                    new RtspEncoder(),
                                    new HttpObjectAggregator(64 * 1024),
                                    new LoggingHandler(LogLevel.INFO, ByteBufFormat.SIMPLE),
                                    new ControlHandler(airPlayConsumer));
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            io.netty.channel.ChannelFuture channelFuture = serverBootstrap.bind().sync();

            port = ((InetSocketAddress) channelFuture.channel().localAddress()).getPort();
            log.info("AirPlay control server listening on port: " + port);

            synchronized (this) {
                this.notify();
            }

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            log.info("AirPlay control server stopped");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private EventLoopGroup eventLoopGroup() {
        return Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
    }

    private Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }
}
