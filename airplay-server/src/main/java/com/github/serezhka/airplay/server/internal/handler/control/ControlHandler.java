package com.github.serezhka.airplay.server.internal.handler.control;

import com.dd.plist.BinaryPropertyListParser;
import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConfig;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.handler.session.Session;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import com.github.serezhka.airplay.server.internal.handler.util.PropertyListUtil;
import io.lindstrom.m3u8.model.*;
import io.lindstrom.m3u8.parser.MasterPlaylistParser;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.lindstrom.m3u8.parser.ParsingMode;
import io.lindstrom.m3u8.parser.PlaylistParserException;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.rtsp.*;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

public class ControlHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger log = Logger.getLogger(ControlHandler.class.getName());

    private final SessionManager sessionManager = new SessionManager();
    private final AirPlayConfig airPlayConfig = new AirPlayConfig();
    private final AirPlayConsumer airPlayConsumer;
    
    public ControlHandler(AirPlayConsumer airPlayConsumer) {
        this.airPlayConsumer = airPlayConsumer;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            if (RtspVersions.RTSP_1_0.equals(request.protocolVersion())) {
                if (HttpMethod.GET.equals(request.method()) && "/info".equals(request.uri())) {
                    handleGetInfo(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && "/pair-setup".equals(request.uri())) {
                    handlePairSetup(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && "/pair-verify".equals(request.uri())) {
                    handlePairVerify(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && "/fp-setup".equals(request.uri())) {
                    handleFairPlaySetup(ctx, request);
                } else if (RtspMethods.SETUP.equals(request.method())) {
                    handleRtspSetup(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && "/feedback".equals(request.uri())) {
                    handleRtspFeedback(ctx, request);
                } else if (RtspMethods.GET_PARAMETER.equals(request.method())) {
                    handleRtspGetParameter(ctx, request);
                } else if (RtspMethods.RECORD.equals(request.method())) {
                    handleRtspRecord(ctx, request);
                } else if (RtspMethods.SET_PARAMETER.equals(request.method())) {
                    handleRtspSetParameter(ctx, request);
                } else if ("FLUSH".equals(request.method().toString())) {
                    handleRtspFlush(ctx, request);
                } else if (RtspMethods.TEARDOWN.equals(request.method())) {
                    handleRtspTeardown(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && request.uri().equals("/audioMode")) {
                    handleRtspAudioMode(ctx, request);
                } else {
                    log.severe("Unknown control request: " + request.protocolVersion() + " " + request.method() + " " + request.uri());
                    DefaultFullHttpResponse response = createRtspResponse(request);
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    sendResponse(ctx, request, response);
                }
            } else if (HttpVersion.HTTP_1_1.equals(request.protocolVersion())) {
                QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
                if (HttpMethod.GET.equals(request.method()) && decoder.path().equals("/server-info")) {
                    handleGetServerInfo(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/fp-setup")) {
                    // TODO handleFairPlaySetup(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/fp-setup2")) {
                    // TODO handleFairPlaySetup2(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/reverse")) {
                    handleReverse(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/play")) {
                    handlePlay(ctx, request);
                } else if (HttpMethod.PUT.equals(request.method()) && decoder.path().equals("/setProperty")) {
                    handleSetProperty(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/rate")) {
                    handleRate(ctx, request);
                } else if (HttpMethod.GET.equals(request.method()) && decoder.path().equals("/playback-info")) {
                    handlePlaybackInfo(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/action")) {
                    handleAction(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/getProperty")) {
                    handleGetProperty(ctx, request);
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/scrub")) {
                    log.info(request.uri()); // TODO
                } else if (HttpMethod.POST.equals(request.method()) && decoder.path().equals("/stop")) {
                    log.info(request.uri()); // TODO
                } else if (HttpMethod.GET.equals(request.method()) && decoder.path().startsWith("/playlist")) {
                    handleGetPlaylist(ctx, request);
                } else {
                    log.severe("Unknown control request: " + request.protocolVersion() + " " + request.method() + " " + request.uri());
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                    sendResponse(ctx, request, response);
                }
            }
        } else if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            // reverse connection response
        } else {
            log.severe("Unknown control message type: {}" + msg);
        }
    }

    /**
     * Resolves session by the request headers:<br/>
     * {@code Active-Remote} for RTSP<br/>
     * {@code X-Apple-Session-ID} for HTTP
     *
     * @param request incoming request
     * @return active session
     */
    private Session resolveSession(FullHttpRequest request) {
        String sessionId = Optional.ofNullable(request.headers().get("Active-Remote"))
                .orElseGet(() -> request.headers().get("X-Apple-Session-ID"));
        return sessionManager.getSession(sessionId);
    }

    private void handleGetInfo(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        byte[] info = PropertyListUtil.prepareInfoResponse(airPlayConfig);
        DefaultFullHttpResponse response = createRtspResponse(request);
        response.content().writeBytes(info);
        sendResponse(ctx, request, response);
    }

    private void handlePairSetup(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = resolveSession(request);
        DefaultFullHttpResponse response = createRtspResponse(request);
        session.getAirPlay().pairSetup(new ByteBufOutputStream(response.content()));
        sendResponse(ctx, request, response);
    }

    private void handlePairVerify(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = resolveSession(request);
        DefaultFullHttpResponse response = createRtspResponse(request);
        session.getAirPlay().pairVerify(new ByteBufInputStream(request.content()),
                new ByteBufOutputStream(response.content()));
        sendResponse(ctx, request, response);
    }

    private void handleFairPlaySetup(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = resolveSession(request);
        DefaultFullHttpResponse response = createRtspResponse(request);
        session.getAirPlay().fairPlaySetup(new ByteBufInputStream(request.content()),
                new ByteBufOutputStream(response.content()));
        sendResponse(ctx, request, response);
    }

    /*private void handleFairPlaySetup2(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        sendResponse(ctx, request, response);
    }*/

    private void handleRtspSetup(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = resolveSession(request);
        DefaultFullHttpResponse response = createRtspResponse(request);
        Optional<com.github.serezhka.airplay.lib.MediaStreamInfo> mediaStreamInfo = session.getAirPlay().rtspSetup(new ByteBufInputStream(request.content()));
        if (mediaStreamInfo.isPresent()) {
            switch (mediaStreamInfo.get().getStreamType()) {
                case AUDIO:
                    airPlayConsumer.onAudioFormat((AudioStreamInfo) mediaStreamInfo.get());
                    session.getAudioServer().start(airPlayConsumer);
                    session.getAudioControlServer().start();
                    byte[] setup = PropertyListUtil.prepareSetupAudioResponse(session.getAudioServer().getPort(),
                            session.getAudioControlServer().getPort());
                    response.content().writeBytes(setup);
                    break;
                case VIDEO:
                    airPlayConsumer.onVideoFormat((VideoStreamInfo) mediaStreamInfo.get());
                    session.getVideoServer().start(airPlayConsumer);
                    byte[] setupVideo = PropertyListUtil.prepareSetupVideoResponse(session.getVideoServer().getPort(),
                            ((ServerSocketChannel) ctx.channel().parent()).localAddress().getPort(), 0);
                    response.content().writeBytes(setupVideo);
                    break;
            }
        }
        sendResponse(ctx, request, response);
    }

    private void handleRtspFeedback(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = createRtspResponse(request);
        sendResponse(ctx, request, response);
    }

    private void handleRtspGetParameter(ChannelHandlerContext ctx, FullHttpRequest request) {
        // TODO get requested param and respond accordingly
        byte[] content = "volume: 0.000000\r\n".getBytes(StandardCharsets.US_ASCII);
        DefaultFullHttpResponse response = createRtspResponse(request);
        response.content().writeBytes(content);
        sendResponse(ctx, request, response);
    }

    private void handleRtspRecord(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = createRtspResponse(request);
        response.headers().add("Audio-Latency", "11025");
        response.headers().add("Audio-Jack-Status", "connected; type=analog");
        sendResponse(ctx, request, response);
    }

    private void handleRtspSetParameter(ChannelHandlerContext ctx, FullHttpRequest request) {
        // TODO get requested param and respond accordingly
        DefaultFullHttpResponse response = createRtspResponse(request);
        response.headers().add("Audio-Jack-Status", "connected; type=analog");
        sendResponse(ctx, request, response);
    }

    private void handleRtspFlush(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = createRtspResponse(request);
        sendResponse(ctx, request, response);
    }

    private void handleRtspTeardown(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = resolveSession(request);
        Optional<com.github.serezhka.airplay.lib.MediaStreamInfo> mediaStreamInfo = session.getAirPlay().rtspTeardown(new ByteBufInputStream(request.content()));
        if (mediaStreamInfo.isPresent()) {
            switch (mediaStreamInfo.get().getStreamType()) {
                case AUDIO:
                    airPlayConsumer.onAudioSrcDisconnect();
                    session.getAudioServer().stop();
                    session.getAudioControlServer().stop();
                    break;
                case VIDEO:
                    airPlayConsumer.onVideoSrcDisconnect();
                    session.getVideoServer().stop();
                    break;
            }
        } else {
            airPlayConsumer.onAudioSrcDisconnect();
            airPlayConsumer.onVideoSrcDisconnect();
            session.getAudioServer().stop();
            session.getAudioControlServer().stop();
            session.getVideoServer().stop();
        }
        DefaultFullHttpResponse response = createRtspResponse(request);
        sendResponse(ctx, request, response);
    }

    private void handleRtspAudioMode(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = createRtspResponse(request);
        sendResponse(ctx, request, response);
    }

    private void handleGetServerInfo(ChannelHandlerContext ctx, FullHttpRequest request) {
        byte[] serverInfo = PropertyListUtil.prepareServerInfoResponse();
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/x-apple-plist+xml");
        response.content().writeBytes(serverInfo);
        sendResponse(ctx, request, response);
    }

    private void handleReverse(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        response.headers().add(HttpHeaderNames.UPGRADE, request.headers().get(HttpHeaderNames.UPGRADE));
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
        sendResponse(ctx, request, response);

        String purpose = request.headers().get("X-Apple-Purpose");
        ctx.pipeline().remove(RtspDecoder.class);
        ctx.pipeline().remove(RtspEncoder.class);
        ctx.pipeline().addFirst(new HttpClientCodec());
        Session session = resolveSession(request);
        session.getReverseContexts().put(purpose, ctx);
    }

    private void handlePlay(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        NSDictionary play = (NSDictionary) BinaryPropertyListParser.parse(new ByteBufInputStream(request.content()));
        log.info("Request content:\n{}" + play.toXMLPropertyList());

        String clientProcName = play.get("clientProcName").toJavaObject(String.class);
        if ("YouTube".equals(clientProcName)) {
            Session session = resolveSession(request);
            String playlistUri = play.get("Content-Location").toJavaObject(String.class);
            String playlistUriLocal = playlistUriToLocal(playlistUri, playlistBaseUrl(ctx), session.getId());

            // TODO Create MediaPlaylist record with UUID
            airPlayConsumer.onMediaPlaylist(playlistUriLocal);

            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            sendResponse(ctx, request, response);
        } else {
            log.severe("Client proc name [{}] is not supported!" + clientProcName);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
            sendResponse(ctx, request, response);
        }
    }

    private void handleSetProperty(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        log.info("Path: {}, Query params: {}" + decoder.path() + ", " + decoder.parameters());
        NSDictionary play = (NSDictionary) BinaryPropertyListParser.parse(new ByteBufInputStream(request.content()));
        log.info("Request content:\n{}" + ", " + play.toXMLPropertyList());

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        sendResponse(ctx, request, response);
    }

    private void handleRate(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        int rate = (int) Double.parseDouble(decoder.parameters().get("value").get(0));

        if (rate == 0) {
            airPlayConsumer.onMediaPlaylistPause();
        } else {
            airPlayConsumer.onMediaPlaylistResume();
        }

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        sendResponse(ctx, request, response);
    }

    private void handlePlaybackInfo(ChannelHandlerContext ctx, FullHttpRequest request) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/x-apple-plist+xml");
        byte[] playbackInfo = PropertyListUtil.preparePlaybackInfoResponse(airPlayConsumer.playbackInfo());
        response.content().writeBytes(playbackInfo);
        sendResponse(ctx, request, response);
    }

    private void handleAction(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        NSDictionary action = (NSDictionary) BinaryPropertyListParser.parse(new ByteBufInputStream(request.content()));
        log.info("Request content:\n{}" + action.toXMLPropertyList());

        String type = action.get("type").toJavaObject(String.class);
        if ("unhandledURLResponse".equals(type)) {
            NSDictionary params = (NSDictionary) action.get("params");
            String fcupResponseURL = params.get("FCUP_Response_URL").toJavaObject(String.class);
            String fcupResponseBase64 = ((NSData) (params.get("FCUP_Response_Data"))).getBase64EncodedData();
            String fcupResponse = new String(Base64.getDecoder().decode(fcupResponseBase64));
            Session session = resolveSession(request);

            if (session.getPlaylistRequestContexts().containsKey(fcupResponseURL)) {
                if (fcupResponseURL.contains("master.m3u8")) {
                    ChannelHandlerContext context = session.getPlaylistRequestContexts().get(fcupResponseURL);
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.content().writeCharSequence(masterPlaylistToLocalUrls(fcupResponse, playlistBaseUrl(ctx), session.getId()), StandardCharsets.UTF_8);
                    HttpUtil.setContentLength(response, response.content().readableBytes());
                    context.writeAndFlush(response);
                    session.getPlaylistRequestContexts().remove(fcupResponseURL);
                } else if (fcupResponseURL.contains("mediadata.m3u8")) {
                    MediaPlaylistParser parser = new MediaPlaylistParser(ParsingMode.LENIENT);
                    MediaPlaylist mediaPlaylist = parser.readPlaylist(fcupResponse);

                    // 手动处理condensedUrl，替换Stream API
                    Map<String, String> condensedUrl = new HashMap<>();
                    
                    // 查找YT-EXT-CONDENSED-URL注释
                    for (String comment : mediaPlaylist.comments()) {
                        if (comment.startsWith("YT-EXT-CONDENSED-URL:")) {
                            String attributes = comment.replace("YT-EXT-CONDENSED-URL:", "");
                            
                            // 手动解析属性，替换flatMap和Pattern.results()
                            Pattern pattern = Pattern.compile("([A-Z0-9\\-]+)=(?:\"([^\"]+)\"|([^,]+))");
                            Matcher matcher = pattern.matcher(attributes);
                            while (matcher.find()) {
                                String key = matcher.group(1);
                                String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                                condensedUrl.put(key, value);
                            }
                            break; // 只处理第一个匹配的注释
                        }
                    }

                    if (!condensedUrl.isEmpty()) {
                        // 手动处理mediaSegments，替换Stream API
                        java.util.List<MediaSegment> newMediaSegments = new java.util.ArrayList<>();
                        for (MediaSegment segment : mediaPlaylist.mediaSegments()) {
                            String prefix = condensedUrl.get("PREFIX");
                            String[] paramNames = condensedUrl.get("PARAMS").split(",");
                            String[] paramValues = segment.uri().replaceFirst(prefix, "").split("/");
                            StringBuilder paramResult = new StringBuilder();
                            for (int i = 0; i < paramNames.length; i++) {
                                paramResult.append("/").append(paramNames[i]).append("/").append(paramValues[i]);
                            }
                            MediaSegment newSegment = MediaSegment.builder()
                                    .from(segment)
                                    .uri(condensedUrl.get("BASE-URI") + paramResult.toString())
                                    .build();
                            newMediaSegments.add(newSegment);
                        }
                        
                        mediaPlaylist = MediaPlaylist.builder()
                                .from(mediaPlaylist)
                                .mediaSegments(newMediaSegments)
                                .build();
                    }

                    ChannelHandlerContext context = session.getPlaylistRequestContexts().get(fcupResponseURL);
                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.content().writeCharSequence(parser.writePlaylistAsString(mediaPlaylist), StandardCharsets.UTF_8);
                    HttpUtil.setContentLength(response, response.content().readableBytes());
                    context.writeAndFlush(response);
                    session.getPlaylistRequestContexts().remove(fcupResponseURL);
                }
            }
        } else if ("playlistRemove".equals(type)) {
            /*<plist version="1.0">
            <dict>
            	<key>type</key>
            	<string>playlistRemove</string>
            	<key>params</key>
            	<dict>
            		<key>item</key>
            		<dict>
            			<key>uuid</key>
            			<string>59F93E62-4E79-4A8F-A55A-D7DA65247AF1</string>
            		</dict>
            	</dict>
            </dict>
            </plist>*/
            airPlayConsumer.onMediaPlaylistRemove();
        }

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        sendResponse(ctx, request, response);
    }

    private void handleGetProperty(ChannelHandlerContext ctx, FullHttpRequest request) {
        // TODO get requested param and respond accordingly
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        log.info("Path: {}, Query params: {}" + decoder.path() + ", " + decoder.parameters());
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        sendResponse(ctx, request, response);
    }

    private void handleGetPlaylist(ChannelHandlerContext ctx, FullHttpRequest request) {
        String playlistUriRemote = playlistPathToRemote(request.uri());
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Session session = sessionManager.getSession(decoder.parameters().get("session").get(0));
        session.getPlaylistRequestContexts().put(playlistUriRemote, ctx);
        sendEventRequest(session, playlistUriRemote);
    }

    private String playlistUriToLocal(String playlistUri, String baseUrl, String sessionId) {
        String playlistUriLocal = playlistUri.replace("mlhls://localhost", baseUrl);
        QueryStringEncoder queryEncoder = new QueryStringEncoder(playlistUriLocal);
        queryEncoder.addParam("session", sessionId);
        return queryEncoder.toString();
    }

    private String playlistPathToRemote(String playlistPath) {
        String playlistUriLocal = "mlhls://localhost" + playlistPath.replace("/playlist", "");
        return playlistUriLocal.split("\\?")[0]; // remove query
    }

    private String playlistBaseUrl(ChannelHandlerContext ctx) {
        int port = ((ServerSocketChannel) ctx.channel().parent()).localAddress().getPort();
        return String.format("http://localhost:%s/playlist", port);
    }

    private String masterPlaylistToLocalUrls(String masterPlaylist, String baseUrl, String sessionId) throws PlaylistParserException {
        MasterPlaylistParser parser = new MasterPlaylistParser();
        MasterPlaylist playlist = parser.readPlaylist(masterPlaylist);

        // 手动处理alternativeRenditions，替换Stream API
        java.util.List<AlternativeRendition> newAlternativeRenditions = new java.util.ArrayList<>();
        for (AlternativeRendition rendition : playlist.alternativeRenditions()) {
            if (rendition.uri().isPresent()) {
                AlternativeRendition newRendition = AlternativeRendition.builder()
                        .from(rendition)
                        .uri(playlistUriToLocal(rendition.uri().get(), baseUrl, sessionId))
                        .build();
                newAlternativeRenditions.add(newRendition);
            } else {
                newAlternativeRenditions.add(rendition);
            }
        }

        // 手动处理variants，替换Stream API
        java.util.List<Variant> newVariants = new java.util.ArrayList<>();
        for (Variant variant : playlist.variants()) {
            Variant newVariant = Variant.builder()
                    .from(variant)
                    .uri(playlistUriToLocal(variant.uri(), baseUrl, sessionId))
                    .build();
            newVariants.add(newVariant);
        }

        playlist = MasterPlaylist.builder()
                .from(playlist)
                .alternativeRenditions(newAlternativeRenditions)
                .variants(newVariants)
                .build();

        return parser.writePlaylistAsString(playlist);
    }

    private DefaultFullHttpResponse createRtspResponse(FullHttpRequest request) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        response.headers().clear();

        String cSeq = request.headers().get(RtspHeaderNames.CSEQ);
        if (cSeq != null) {
            response.headers().add(RtspHeaderNames.CSEQ, cSeq);
            response.headers().add(RtspHeaderNames.SERVER, "AirTunes/220.68");
        }

        return response;
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void sendEventRequest(Session session, String listUri) {
        byte[] requestContent = PropertyListUtil.prepareEventRequest(session.getId(), listUri);

        DefaultFullHttpRequest event = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/event");
        event.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/x-apple-plist+xml");
        event.headers().add(HttpHeaderNames.CONTENT_LENGTH, requestContent.length);
        event.headers().add("X-Apple-Session-ID", session.getId());
        event.content().writeBytes(requestContent);

        session.getReverseContexts().get("event").writeAndFlush(event);
    }
}
