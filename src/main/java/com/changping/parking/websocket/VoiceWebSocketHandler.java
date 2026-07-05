package com.changping.parking.websocket;

import com.changping.parking.dialog.DialogManager;
import com.changping.parking.model.CallSession;
import com.changping.parking.model.CallSessionManager;
import com.changping.parking.model.DialogState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 语音对话处理器
 * 
 * 处理浏览器与服务端的实时语音对话，支持文本消息和音频数据传输。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {

    private final DialogManager dialogManager;
    private final CallSessionManager sessionManager;

    /** WebSocket 会话与通话会话的映射 */
    private final Map<String, WebSocketSession> webSocketSessions = new HashMap<>();

    public VoiceWebSocketHandler(DialogManager dialogManager, CallSessionManager sessionManager) {
        this.dialogManager = dialogManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        webSocketSessions.put(sessionId, session);
        log.info("WebSocket 连接建立: {}", sessionId);

        CallSession callSession = sessionManager.createSession(sessionId);
        callSession.transitionTo(DialogState.IDENTIFYING_PARK);

        String welcome = "您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？";
        
        sendMessage(sessionId, "text", welcome);
        sendMessage(sessionId, "speak", welcome);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        try {
            CallSession callSession = sessionManager.getSession(sessionId);
            if (callSession == null) {
                log.warn("会话不存在: {}", sessionId);
                return;
            }

            if (payload.startsWith("text:")) {
                String text = payload.substring(5);
                log.debug("收到文本消息: sessionId={}, text={}", sessionId, text);
                
                String answer = dialogManager.processTextMessage(sessionId, text);
                
                if (answer != null) {
                    sendMessage(sessionId, "text", answer);
                    sendMessage(sessionId, "speak", answer);
                }

                if (callSession.getState() == DialogState.END) {
                    session.close(CloseStatus.NORMAL);
                }
            } else if (payload.startsWith("audio:")) {
                String audioBase64 = payload.substring(6);
                log.debug("收到音频数据: sessionId={}, length={}", sessionId, audioBase64.length());
                
                dialogManager.processAudioMessage(sessionId, audioBase64);
            } else if (payload.equals("ping")) {
                sendMessage(sessionId, "pong", "");
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
            sendMessage(sessionId, "error", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        webSocketSessions.remove(sessionId);
        
        CallSession callSession = sessionManager.getSession(sessionId);
        if (callSession != null) {
            dialogManager.handleEnd(callSession);
            sessionManager.removeSession(sessionId);
        }
        
        log.info("WebSocket 连接关闭: {}", sessionId);
    }

    /**
     * 发送消息
     * 
     * @param sessionId 会话 ID
     * @param type 消息类型 (text, speak, error, pong)
     * @param content 消息内容
     */
    public void sendMessage(String sessionId, String type, String content) {
        WebSocketSession webSocketSession = webSocketSessions.get(sessionId);
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                String message = type + ":" + content;
                webSocketSession.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("发送消息失败: {}", e.getMessage());
            }
        }
    }
}