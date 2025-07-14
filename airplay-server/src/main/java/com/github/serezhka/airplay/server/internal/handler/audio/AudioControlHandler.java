package com.github.serezhka.airplay.server.internal.handler.audio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import java.util.logging.Logger;

public class AudioControlHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    
    private static final Logger log = Logger.getLogger(AudioControlHandler.class.getName());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        ByteBuf content = msg.content();
        int contentLength = content.readableBytes();
        byte[] contentBytes = new byte[contentLength];
        content.readBytes(contentBytes);
        int type = contentBytes[1] & ~0x80;
        log.fine("Got audio control packet, type: " + type + ", length: " + contentLength);
    }
}
