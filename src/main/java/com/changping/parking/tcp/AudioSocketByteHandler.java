package com.changping.parking.tcp;

import com.changping.parking.dialog.DialogManager;
import com.changping.parking.model.CallSession;
import com.changping.parking.model.CallSessionManager;
import com.changping.parking.service.CallRecordService;
import com.changping.parking.speech.AsrService;
import com.changping.parking.speech.TtsService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Sharable
@Component
public class AudioSocketByteHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final DialogManager dialogManager;
    private final CallSessionManager sessionManager;
    private final AsrService asrService;
    private final TtsService ttsService;
    private final CallRecordService callRecordService;

    private StringBuilder buffer = new StringBuilder();

    public AudioSocketByteHandler(@Lazy DialogManager dialogManager,
                                   CallSessionManager sessionManager,
                                   AsrService asrService,
                                   TtsService ttsService,
                                   CallRecordService callRecordService) {
        this.dialogManager = dialogManager;
        this.sessionManager = sessionManager;
        this.asrService = asrService;
        this.ttsService = ttsService;
        this.callRecordService = callRecordService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("新的 FreeSWITCH 连接建立: {}", ctx.channel().remoteAddress());
        buffer = new StringBuilder();
        // 发送 connect 确认，告诉 FreeSWITCH 本端已就绪，开始接收事件
        sendCommand(ctx, "connect");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        String data = new String(bytes, StandardCharsets.ISO_8859_1);
        buffer.append(data);

        // 循环处理所有完整消息
        while (true) {
            String content = buffer.toString();
            int doubleNewlineIdx = content.indexOf("\n\n");
            if (doubleNewlineIdx < 0) {
                break;
            }

            String message = content.substring(0, doubleNewlineIdx);
            int headerEnd = doubleNewlineIdx + 2;

            // 提取消息体
            int contentLength = extractContentLength(message);
            byte[] body = new byte[0];

            if (contentLength > 0) {
                // 仅 text/event-plain 类型才等待完整 body
                boolean isWrapped = message.contains("Content-Type: text/event-plain");
                if (isWrapped && content.length() < headerEnd + contentLength) {
                    break; // body 还没收完，等下一次 channelRead
                }
                if (content.length() >= headerEnd + contentLength) {
                    String bodyStr = content.substring(headerEnd, headerEnd + contentLength);
                    body = bodyStr.getBytes(StandardCharsets.ISO_8859_1);
                }
            }

            // 从缓冲中移除已处理的数据
            int removeLen = (contentLength > 0 && content.length() >= headerEnd + contentLength)
                    ? headerEnd + contentLength : headerEnd;
            buffer.delete(0, removeLen);

            processMessage(ctx, message, body);
        }
    }

    private void processMessage(ChannelHandlerContext ctx, String message, byte[] body) {
        log.debug("收到 FreeSWITCH 消息:\n{}", message);

        if (message.contains("text/disconnect-notice")) {
            handleHangup(ctx, message);
        } else if (message.contains("Content-Type: text/event-plain") && message.contains("Event-Name: custom")) {
            // 跳过自定义事件
        } else if (message.contains("Content-Type: text/event-plain") && body.length > 0) {
            // text/event-plain 事件：事件数据在 body 中，需要解析 body
            handleTextEventPlain(ctx, body);
        } else if (message.contains("Event-Name: CHANNEL_DATA") && message.contains("Unique-ID")
                && findSessionId(ctx) == null) {
            // 首个 CHANNEL_DATA 事件包含通道变量，是连接建立事件，不是音频数据
            handleConnect(ctx, message);
        } else if (message.contains("Event-Name: CHANNEL_DATA")) {
            handleChannelData(ctx, message, body);
        } else if (message.contains("Event-Name: CHANNEL_HANGUP")) {
            handleHangup(ctx, message);
        } else if (message.contains("Content-Type: text/event-plain") && message.contains("Event-Name: CHANNEL_ANSWER")) {
            // 通道已应答
        } else if (message.contains("Content-Type: command/reply") || message.contains("Content-Type: api/response")) {
            // 跳过命令响应
        }
    }

    /**
     * 处理 text/event-plain 事件的 body 内容
     * body 中包含实际的事件数据（如 CHANNEL_DATA、CHANNEL_HANGUP 等）
     */
    private void handleTextEventPlain(ChannelHandlerContext ctx, byte[] body) {
        String bodyStr = new String(body, StandardCharsets.ISO_8859_1);
        int doubleNewlineIdx = bodyStr.indexOf("\n\n");

        String bodyHeaders;
        byte[] innerBody = new byte[0];

        if (doubleNewlineIdx >= 0) {
            bodyHeaders = bodyStr.substring(0, doubleNewlineIdx);
            int innerContentLength = extractContentLength(bodyHeaders);
            if (innerContentLength > 0 && doubleNewlineIdx + 2 + innerContentLength <= bodyStr.length()) {
                String innerBodyStr = bodyStr.substring(doubleNewlineIdx + 2, doubleNewlineIdx + 2 + innerContentLength);
                innerBody = innerBodyStr.getBytes(StandardCharsets.ISO_8859_1);
            }
        } else {
            bodyHeaders = bodyStr;
        }

        log.info("text/event-plain body: {}", bodyHeaders.substring(0, Math.min(300, bodyHeaders.length())));

        if (bodyHeaders.contains("Event-Name: CHANNEL_DATA")) {
            log.info("text/event-plain 中包含 CHANNEL_DATA, innerBody长度={}", innerBody.length);
            handleChannelData(ctx, bodyHeaders, innerBody);
        } else if (bodyHeaders.contains("Event-Name: CHANNEL_HANGUP") || bodyHeaders.contains("text/disconnect-notice")) {
            handleHangup(ctx, bodyHeaders);
        } else if (bodyHeaders.contains("Event-Name: CHANNEL_ANSWER")) {
            log.info("text/event-plain: 通道已应答");
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, String msg) {
        Map<String, String> headers = parseHeaders(msg);
        String uuid = headers.getOrDefault("Unique-ID", UUID.randomUUID().toString());
        String callerNumber = headers.getOrDefault("Caller-Caller-ID-Number", "unknown");
        String calledNumber = headers.getOrDefault("Caller-Destination-Number", "unknown");

        log.info("通话连接: uuid={}, caller={}, called={}", uuid, callerNumber, calledNumber);

        CallSession session = sessionManager.createSession(uuid);
        session.setChannel(ctx.channel());
        session.setCallerNumber(callerNumber);
        session.setCalledNumber(calledNumber);

        sendCommand(ctx, "myevents");
        sendCommand(ctx, "answer");

        asrService.startSession(uuid);
        session.setAsrStarted(true);

        // 异步执行欢迎流程，避免阻塞 Netty 事件循环
        new Thread(() -> dialogManager.handleWelcome(session), "welcome-" + uuid).start();
    }

    private void handleChannelData(ChannelHandlerContext ctx, String msg, byte[] audioData) {
        String sessionId = extractHeaderValue(msg, "Unique-ID");
        if (sessionId == null) {
            sessionId = findSessionId(ctx);
        }

        if (sessionId != null && audioData.length > 0) {
            asrService.feedAudio(sessionId, audioData);
        }
    }

    private void handleHangup(ChannelHandlerContext ctx, String msg) {
        String sessionId = findSessionId(ctx);
        log.info("通话挂断: sessionId={}", sessionId);

        if (sessionId != null) {
            CallSession session = sessionManager.getSession(sessionId);
            if (session != null) {
                callRecordService.saveRecord(session);
            }
            asrService.stopSession(sessionId);
            sessionManager.removeSession(sessionId);
        }

        ctx.close();
    }

    public void playAudio(String sessionId, String filePath) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session != null && session.getChannel() != null) {
            // 必须在 event loop 线程中发送，与 sendCommand 一致
            session.getChannel().eventLoop().execute(() -> {
                String command = "sendmsg " + sessionId + "\n" +
                        "call-command: execute\n" +
                        "execute-app-name: playback\n" +
                        "execute-app-arg: " + filePath + "\n\n";
                byte[] data = command.getBytes(StandardCharsets.ISO_8859_1);
                session.getChannel().writeAndFlush(
                        session.getChannel().alloc().buffer(data.length).writeBytes(data));
                log.info("发送播放命令: sessionId={}, file={}", sessionId, filePath);
            });
        }
    }

    public void playAudioInline(String sessionId, String text) {
        String wavPath = ttsService.synthesize(sessionId, text);
        if (wavPath != null) {
            playAudio(sessionId, wavPath);
        }
    }

    public void hangup(String sessionId) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session != null && session.getChannel() != null) {
            session.getChannel().eventLoop().execute(() -> {
                String command = "sendmsg " + sessionId + "\n" +
                        "call-command: execute\n" +
                        "execute-app-name: hangup\n\n";
                byte[] data = command.getBytes(StandardCharsets.ISO_8859_1);
                session.getChannel().writeAndFlush(
                        session.getChannel().alloc().buffer(data.length).writeBytes(data));
            });
        }
    }

    private void sendCommand(ChannelHandlerContext ctx, String command) {
        byte[] data = (command + "\n\n").getBytes(StandardCharsets.ISO_8859_1);
        ctx.writeAndFlush(ctx.alloc().buffer(data.length).writeBytes(data));
        log.debug("发送命令: {}", command);
    }

    private Map<String, String> parseHeaders(String msg) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = msg.split("\n");
        for (String line : lines) {
            int colonIdx = line.indexOf(":");
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    private String extractHeaderValue(String msg, String headerName) {
        String lowerMsg = msg.toLowerCase();
        String lowerHeader = headerName.toLowerCase() + ":";
        int idx = lowerMsg.indexOf(lowerHeader);
        if (idx >= 0) {
            int endIdx = msg.indexOf("\n", idx);
            if (endIdx > idx) {
                return msg.substring(idx + lowerHeader.length(), endIdx).trim();
            }
        }
        return null;
    }

    private int extractContentLength(String msg) {
        String value = extractHeaderValue(msg, "Content-Length");
        return value != null ? Integer.parseInt(value) : 0;
    }

    private String findSessionId(ChannelHandlerContext ctx) {
        for (CallSession session : sessionManager.getAllSessions()) {
            if (session.getChannel() != null && session.getChannel().equals(ctx.channel())) {
                return session.getSessionId();
            }
        }
        return null;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String sessionId = findSessionId(ctx);
        log.info("连接断开: sessionId={}", sessionId);
        // 只停止 ASR，session 由 handleHangup 统一管理，避免欢迎线程还在运行时 session 被误删
        if (sessionId != null) {
            asrService.stopSession(sessionId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AudioSocketByteHandler 异常", cause);
        ctx.close();
    }
}
