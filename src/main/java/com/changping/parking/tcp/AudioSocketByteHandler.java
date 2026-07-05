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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 记录每个会话当前的录音阶段，避免重复触发 */
    private final Map<String, String> recordState = new ConcurrentHashMap<>();

    /** 录音文件存放目录 */
    private static final String RECORD_DIR = "C:/Users/yujin/Desktop/IntelligentParkingServiceSystem/temp/record/";

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
        } else if (bodyHeaders.contains("Event-Name: CHANNEL_EXECUTE_COMPLETE")) {
            handleExecuteComplete(ctx, bodyHeaders);
        }
    }

    /**
     * 处理应用执行完成事件
     * 当 playback 完成后开始录音，当 record 完成后读取录音文件并喂给 ASR
     */
    private void handleExecuteComplete(ChannelHandlerContext ctx, String bodyHeaders) {
        String sessionId = extractHeaderValue(bodyHeaders, "Unique-ID");
        if (sessionId == null) {
            sessionId = findSessionId(ctx);
        }
        if (sessionId == null) {
            log.warn("CHANNEL_EXECUTE_COMPLETE 但找不到 sessionId");
            return;
        }

        String application = extractHeaderValue(bodyHeaders, "variable_current_application");
        log.info("应用执行完成: sessionId={}, application={}", sessionId, application);

        if ("playback".equalsIgnoreCase(application)) {
            // 播放完成，开始后台录音
            startRecording(sessionId);
        } else if ("stop_record_session".equalsIgnoreCase(application)) {
            // 停止录音完成，读取录音文件喂给 ASR
            processRecording(sessionId);
        }
    }

    /**
     * 开始录音：使用 record_session 后台录音，不阻塞通道
     * 5秒后自动发送 stop_record_session 停止
     */
    private void startRecording(String sessionId) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session == null || session.getChannel() == null) {
            return;
        }

        if ("recording".equals(recordState.get(sessionId))) {
            return;
        }
        recordState.put(sessionId, "recording");

        String recordFile = RECORD_DIR + sessionId + "_" + System.currentTimeMillis() + ".wav";
        new File(RECORD_DIR).mkdirs();

        final String finalRecordFile = recordFile;

        session.getChannel().eventLoop().execute(() -> {
            String command = "sendmsg " + sessionId + "\n" +
                    "call-command: execute\n" +
                    "execute-app-name: record_session\n" +
                    "execute-app-arg: " + finalRecordFile + "\n\n";
            byte[] data = command.getBytes(StandardCharsets.ISO_8859_1);
            session.getChannel().writeAndFlush(
                    session.getChannel().alloc().buffer(data.length).writeBytes(data));
            log.info("开始后台录音(record_session): sessionId={}, file={}", sessionId, finalRecordFile);
            session.setRecordFilePath(finalRecordFile);
        });

        // 启动定时器，5秒后停止录音
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                session.getChannel().eventLoop().execute(() -> {
                    String stopCmd = "sendmsg " + sessionId + "\n" +
                            "call-command: execute\n" +
                            "execute-app-name: stop_record_session\n" +
                            "execute-app-arg: " + finalRecordFile + "\n\n";
                    byte[] data = stopCmd.getBytes(StandardCharsets.ISO_8859_1);
                    session.getChannel().writeAndFlush(
                            session.getChannel().alloc().buffer(data.length).writeBytes(data));
                    log.info("停止录音(stop_record_session): sessionId={}", sessionId);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "record-timer-" + sessionId).start();
    }

    /**
     * 录音完成后，读取录音文件并喂给 ASR
     */
    private void processRecording(String sessionId) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return;
        }
        String recordFile = session.getRecordFilePath();
        if (recordFile == null) {
            log.warn("录音文件路径为空: sessionId={}", sessionId);
            return;
        }
        processRecordingFile(sessionId, recordFile);
    }

    /**
     * 读取录音文件并喂给 ASR
     */
    private void processRecordingFile(String sessionId, String recordFile) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return;
        }

        recordState.put(sessionId, "processing");

        new Thread(() -> {
            try {
                File file = new File(recordFile);
                int retry = 0;
                while (!file.exists() && file.length() == 0 && retry < 10) {
                    Thread.sleep(200);
                    retry++;
                }

                if (!file.exists() || file.length() == 0) {
                    log.warn("录音文件不存在或为空: {}", recordFile);
                    return;
                }

                log.info("读取录音文件: {}, 大小={} bytes", recordFile, file.length());

                byte[] audioData = Files.readAllBytes(file.toPath());
                // 跳过 WAV 文件头（44字节），只喂 PCM 数据给 ASR
                int headerSize = 44;
                if (audioData.length <= headerSize) {
                    log.warn("录音文件太小: {}", audioData.length);
                    return;
                }
                byte[] pcmData = new byte[audioData.length - headerSize];
                System.arraycopy(audioData, headerSize, pcmData, 0, pcmData.length);

                log.info("推送录音数据到 ASR: sessionId={}, pcmBytes={}", sessionId, pcmData.length);
                asrService.feedAudio(sessionId, pcmData);

            } catch (Exception e) {
                log.error("处理录音文件失败: sessionId={}", sessionId, e);
            }
        }, "asr-process-" + sessionId).start();
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
            recordState.remove(sessionId);
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
