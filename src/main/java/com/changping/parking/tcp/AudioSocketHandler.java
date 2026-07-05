package com.changping.parking.tcp;

import com.changping.parking.dialog.DialogManager;
import com.changping.parking.model.CallSession;
import com.changping.parking.model.CallSessionManager;
import com.changping.parking.service.CallRecordService;
import com.changping.parking.speech.AsrService;
import com.changping.parking.speech.TtsService;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FreeSWITCH 音频 Socket 处理器
 * 
 * 处理 FreeSWITCH 通过 outbound socket 发送的所有消息，包括：
 * - 通话连接事件（connect）
 * - 音频数据（CHANNEL_DATA）
 * - 挂断事件（CHANNEL_HANGUP）
 * 
 * 使用 @Sharable 注解，支持多个 Channel 共享同一个处理器实例。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Sharable
@Component
public class AudioSocketHandler extends SimpleChannelInboundHandler<String> {

    /** 对话管理器，用于协调对话流程 */
    private final DialogManager dialogManager;

    /** 会话管理器，管理所有通话会话 */
    private final CallSessionManager sessionManager;

    /** ASR 服务，处理语音识别 */
    private final AsrService asrService;

    /** TTS 服务，处理语音合成 */
    private final TtsService ttsService;

    /** 通话记录服务，用于保存通话记录 */
    private final CallRecordService callRecordService;

    /**
     * 构造函数
     * 
     * @param dialogManager 对话管理器（使用 @Lazy 避免循环依赖）
     * @param sessionManager 会话管理器
     * @param asrService ASR 服务
     * @param ttsService TTS 服务
     * @param callRecordService 通话记录服务
     */
    public AudioSocketHandler(@Lazy DialogManager dialogManager,
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

    /**
     * 通道激活时调用（新连接建立）
     * 
     * @param ctx 通道上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("新的 FreeSWITCH 连接建立: {}", ctx.channel().remoteAddress());
    }

    /**
     * 收到消息时调用
     * 
     * @param ctx 通道上下文
     * @param msg 消息内容
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.debug("收到 FreeSWITCH 消息:\n{}", msg);

        if (msg.contains("connect")) {
            handleConnect(ctx, msg);
        } else if (msg.contains("CHANNEL_DATA")) {
            handleChannelData(ctx, msg);
        } else if (msg.contains("CHANNEL_HANGUP")) {
            handleHangup(ctx, msg);
        } else if (msg.contains("playback")) {
            log.debug("收到 playback 响应");
        }
    }

    /**
     * 处理通话连接事件
     * 
     * 解析 FreeSWITCH 发送的连接消息，创建会话，发送必要的命令（connect、myevents、answer），
     * 启动 ASR 会话，并触发欢迎流程。
     * 
     * @param ctx 通道上下文
     * @param msg 消息内容
     */
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

        sendCommand(ctx, "connect");
        sendCommand(ctx, "myevents");
        sendCommand(ctx, "answer");

        asrService.startSession(uuid);
        session.setAsrStarted(true);

        dialogManager.handleWelcome(session);
    }

    /**
     * 处理通道数据事件（音频数据）
     * 
     * 从消息中提取音频数据，发送给 ASR 服务进行识别。
     * 
     * @param ctx 通道上下文
     * @param msg 消息内容
     */
    private void handleChannelData(ChannelHandlerContext ctx, String msg) {
        String sessionId = extractHeaderValue(msg, "Unique-ID");
        if (sessionId == null) {
            sessionId = findSessionId(ctx);
        }

        int contentLength = Integer.parseInt(extractHeaderValue(msg, "Content-Length", "0"));
        if (contentLength > 0) {
            int bodyStart = msg.indexOf("\n\n");
            if (bodyStart > 0 && bodyStart + 2 < msg.length()) {
                byte[] audioData = extractAudioBytes(msg, bodyStart + 2, contentLength);
                if (sessionId != null) {
                    asrService.feedAudio(sessionId, audioData);
                }
            }
        }
    }

    /**
     * 从消息中提取音频字节数据
     * 
     * @param msg 消息内容
     * @param offset 偏移量
     * @param length 数据长度
     * @return 音频字节数组
     */
    private byte[] extractAudioBytes(String msg, int offset, int length) {
        byte[] msgBytes = msg.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        if (offset + length <= msgBytes.length) {
            byte[] audio = new byte[length];
            System.arraycopy(msgBytes, offset, audio, 0, length);
            return audio;
        }
        return new byte[0];
    }

    /**
     * 处理通话挂断事件
     * 
     * 停止 ASR 会话，保存通话记录，清理会话资源，关闭通道。
     * 
     * @param ctx 通道上下文
     * @param msg 消息内容
     */
    private void handleHangup(ChannelHandlerContext ctx, String msg) {
        String sessionId = findSessionId(ctx);
        log.info("通话挂断: sessionId={}", sessionId);

        if (sessionId != null) {
            // 保存通话记录
            CallSession session = sessionManager.getSession(sessionId);
            if (session != null && session.getState() != com.changping.parking.model.DialogState.END) {
                callRecordService.saveRecord(session);
            }

            asrService.stopSession(sessionId);
            sessionManager.removeSession(sessionId);
        }

        ctx.close();
    }

    /**
     * 播放音频文件
     * 
     * 通过 FreeSWITCH 的 playback 命令播放指定的音频文件。
     * 
     * @param sessionId 会话 ID
     * @param filePath 音频文件路径
     */
    public void playAudio(String sessionId, String filePath) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session != null && session.getChannel() != null) {
            String command = "sendmsg\n" +
                    "call-command: execute\n" +
                    "execute-app-name: playback\n" +
                    "execute-app-arg: " + filePath + "\n\n";
            session.getChannel().writeAndFlush(command);
            log.info("发送播放命令: sessionId={}, file={}", sessionId, filePath);
        }
    }

    /**
     * 播放内联文本（先合成再播放）
     * 
     * 将文本通过 TTS 服务合成为音频文件，然后播放。
     * 
     * @param sessionId 会话 ID
     * @param text 要合成的文本
     */
    public void playAudioInline(String sessionId, String text) {
        String wavPath = ttsService.synthesize(sessionId, text);
        if (wavPath != null) {
            playAudio(sessionId, wavPath);
        }
    }

    /**
     * 挂断通话
     * 
     * 通过 FreeSWITCH 的 hangup 命令挂断通话。
     * 
     * @param sessionId 会话 ID
     */
    public void hangup(String sessionId) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session != null && session.getChannel() != null) {
            String command = "sendmsg\n" +
                    "call-command: execute\n" +
                    "execute-app-name: hangup\n\n";
            session.getChannel().writeAndFlush(command);
        }
    }

    /**
     * 发送命令到 FreeSWITCH
     * 
     * @param ctx 通道上下文
     * @param command 命令内容
     */
    private void sendCommand(ChannelHandlerContext ctx, String command) {
        ctx.writeAndFlush(command + "\n\n");
        log.debug("发送命令: {}", command);
    }

    /**
     * 解析消息头
     * 
     * @param msg 消息内容
     * @return 消息头键值对
     */
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

    /**
     * 提取消息头值
     * 
     * @param msg 消息内容
     * @param headerName 消息头名称
     * @return 消息头值，如果不存在返回 null
     */
    private String extractHeaderValue(String msg, String headerName) {
        return extractHeaderValue(msg, headerName, null);
    }

    /**
     * 提取消息头值（带默认值）
     * 
     * @param msg 消息内容
     * @param headerName 消息头名称
     * @param defaultValue 默认值
     * @return 消息头值，如果不存在返回默认值
     */
    private String extractHeaderValue(String msg, String headerName, String defaultValue) {
        String lowerMsg = msg.toLowerCase();
        String lowerHeader = headerName.toLowerCase() + ":";
        int idx = lowerMsg.indexOf(lowerHeader);
        if (idx >= 0) {
            int endIdx = msg.indexOf("\n", idx);
            if (endIdx > idx) {
                return msg.substring(idx + lowerHeader.length(), endIdx).trim();
            }
        }
        return defaultValue;
    }

    /**
     * 根据通道查找会话 ID
     * 
     * @param ctx 通道上下文
     * @return 会话 ID，如果找不到返回 null
     */
    private String findSessionId(ChannelHandlerContext ctx) {
        for (CallSession session : sessionManager.getAllSessions()) {
            if (session.getChannel() != null && session.getChannel().equals(ctx.channel())) {
                return session.getSessionId();
            }
        }
        return null;
    }

    /**
     * 通道关闭时调用
     * 
     * 清理会话资源。
     * 
     * @param ctx 通道上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String sessionId = findSessionId(ctx);
        log.info("连接断开: sessionId={}", sessionId);
        if (sessionId != null) {
            asrService.stopSession(sessionId);
            sessionManager.removeSession(sessionId);
        }
    }

    /**
     * 发生异常时调用
     * 
     * @param ctx 通道上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AudioSocketHandler 异常", cause);
        ctx.close();
    }
}
