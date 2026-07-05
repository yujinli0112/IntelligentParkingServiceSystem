package com.changping.parking.speech;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class MockAsrService implements AsrService {

    private final Map<String, AsrSession> sessions = new ConcurrentHashMap<>();
    private AsrService.AsrResultCallback callback;

    private static class AsrSession {
        long totalBytes = 0;
        long lastSentBytes = 0;
        AtomicInteger mockIndex = new AtomicInteger(0);
    }

    @Override
    public void startSession(String sessionId) {
        sessions.put(sessionId, new AsrSession());
        log.info("Mock ASR 会话开始: {}", sessionId);
    }

    @Override
    public void feedAudio(String sessionId, byte[] audioData) {
        AsrSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.totalBytes += audioData.length;

        if (session.totalBytes > session.lastSentBytes + 16000) {
            session.lastSentBytes = session.totalBytes;
            String mockText = generateMockText(session.mockIndex.getAndIncrement());
            if (mockText != null && callback != null) {
                log.info("Mock ASR 识别结果[{}]: {}", sessionId, mockText);
                callback.onResult(sessionId, mockText, true);
            }
        }
    }

    @Override
    public void stopSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Mock ASR 会话结束: {}", sessionId);
    }

    @Override
    public void setResultCallback(AsrService.AsrResultCallback callback) {
        this.callback = callback;
    }

    private String generateMockText(int index) {
        switch (index) {
            case 0: return "西关那家停车场";
            case 1: return "对的";
            case 2: return "几点关门啊";
            case 3: return "人工客服";
            default: return null;
        }
    }
}