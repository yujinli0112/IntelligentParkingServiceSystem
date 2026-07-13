package com.changping.parking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.changping.parking.websocket.VoiceWebSocketHandler;

/**
 * WebSocket 配置类
 * 
 * 配置语音对话的 WebSocket 端点，支持浏览器与服务端的实时音频流传输。
 * 
 * @author Changping Parking AI Team
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceWebSocketHandler;

    public WebSocketConfig(VoiceWebSocketHandler voiceWebSocketHandler) {
        this.voiceWebSocketHandler = voiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceWebSocketHandler, "/ws/voice")
                .setAllowedOrigins(
                    "http://localhost:8080",
                    "http://localhost:3000",
                    "http://127.0.0.1:8080"
                );
    }
}