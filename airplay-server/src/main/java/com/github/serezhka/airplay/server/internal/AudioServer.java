package com.github.serezhka.airplay.server.internal;

import com.github.serezhka.airplay.lib.AirPlay;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.decoder.AudioDecoder;
import com.github.serezhka.airplay.server.internal.handler.audio.AudioHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DatagramPacketDecoder;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class AudioServer implements Runnable {
    
    private static final Logger log = Logger.getLogger(AudioServer.class.getName());

    private final AirPlay airPlay;

    private Thread thread;
    private AirPlayConsumer airPlayConsumer;
    private int port;
    
    public AudioServer(AirPlay airPlay) {
        this.airPlay = airPlay;
    }
    
    public int getPort() {
        return port;
    }

    public void start(AirPlayConsumer airPlayConsumer) throws InterruptedException {
        this.airPlayConsumer = airPlayConsumer;
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
            airPlayConsumer = null;
        }
    }

    @Override
    public void run() {
        Bootstrap bootstrap = new Bootstrap();
        EventLoopGroup workerGroup = eventLoopGroup();

        try {
            bootstrap
                    .group(workerGroup)
                    .channel(datagramChannelClass())
                    .localAddress(new InetSocketAddress(0)) // bind random port
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        public void initChannel(final DatagramChannel ch) {
                            ch.pipeline().addLast("audioDecoder", new DatagramPacketDecoder(new AudioDecoder()));
                            ch.pipeline().addLast("audioHandler", new AudioHandler(airPlay, airPlayConsumer));
                        }
                    });
            io.netty.channel.ChannelFuture channelFuture = bootstrap.bind().sync();

            port = ((InetSocketAddress) channelFuture.channel().localAddress()).getPort();
            log.info("AirPlay audio server listening on port: " + port);

            synchronized (this) {
                this.notify();
            }

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.info("AirPlay audio server interrupted");
        } finally {
            log.info("AirPlay audio server stopped");
            workerGroup.shutdownGracefully();
        }
    }

    private EventLoopGroup eventLoopGroup() {
        return Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
    }

    private Class<? extends DatagramChannel> datagramChannelClass() {
        return Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class;
    }
}
