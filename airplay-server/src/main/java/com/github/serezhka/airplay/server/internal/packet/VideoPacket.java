package com.github.serezhka.airplay.server.internal.packet;


public class VideoPacket {

    private final int payloadType;
    private final int payloadSize;
    private final byte[] payload;
    
    public VideoPacket(int payloadType, int payloadSize, byte[] payload) {
        this.payloadType = payloadType;
        this.payloadSize = payloadSize;
        this.payload = payload;
    }
    
    public int getPayloadType() {
        return payloadType;
    }
    
    public int getPayloadSize() {
        return payloadSize;
    }
    
    public byte[] getPayload() {
        return payload;
    }
}
