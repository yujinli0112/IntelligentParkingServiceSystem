package com.changping.parking.speech;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "asr.provider", havingValue = "aliyun")
public class AliyunAsrService implements AsrService {

    @Value("${asr.aliyun.appKey}")
    private String appKey;

    @Value("${asr.aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${asr.aliyun.accessKeySecret}")
    private String accessKeySecret;

    @Value("${asr.aliyun.wsUrl:wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1}")
    private String wsUrl;
    private AsrResultCallback callback;
    private final ConcurrentHashMap<String, AliyunAsrSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void startSession(String sessionId) {
        try {
            AliyunAsrSession asrSession = new AliyunAsrSession();
            asrSession.sessionId = sessionId;
            String token = generateToken();
            sessions.put(sessionId, asrSession);
            connectWebSocket(asrSession, token);
            log.info("阿里云 ASR 会话启动: {}", sessionId);
        } catch (Exception e) {
            log.error("阿里云 ASR 启动失败: sessionId={}", sessionId, e);
            sessions.remove(sessionId);
        }
    }

    @Override
    public void feedAudio(String sessionId, byte[] audioData) {
        AliyunAsrSession session = sessions.get(sessionId);
        if (session != null && session.webSocket != null && session.ready) {
            JSONObject payload = new JSONObject();
            payload.put("header", new JSONObject()
                    .fluentPut("message_id", sessionId + System.currentTimeMillis())
                    .fluentPut("task_id", session.taskId)
                    .fluentPut("namespace", "SpeechTranscriber")
                    .fluentPut("name", "SentenceEnd"));
            payload.put("payload", new JSONObject()
                    .fluentPut("audio_format", "pcm")
                    .fluentPut("sample_rate", 8000));

            ByteBuffer bb = ByteBuffer.wrap(audioData);
            session.webSocket.sendBinary(bb, true);
        }
    }

    @Override
    public void stopSession(String sessionId) {
        AliyunAsrSession session = sessions.remove(sessionId);
        if (session != null && session.webSocket != null) {
            session.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "end");
        }
        log.info("阿里云 ASR 会话结束: {}", sessionId);
    }

    @Override
    public void setResultCallback(AsrResultCallback callback) {
        this.callback = callback;
    }

    private String generateToken() {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String signatureNonce = String.valueOf(System.currentTimeMillis());

            String stringToSign = accessKeyId + signatureNonce + timestamp;
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signBytes);

            return signature + "&" + accessKeyId + "&" + signatureNonce + "&" + timestamp;
        } catch (Exception e) {
            log.error("生成阿里云 Token 失败", e);
            return "";
        }
    }

    private void connectWebSocket(AliyunAsrSession session, String token) {
        HttpClient client = HttpClient.newHttpClient();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                session.webSocket = webSocket;
                JSONObject startMsg = new JSONObject();
                JSONObject header = new JSONObject();
                header.put("message_id", session.sessionId + "_start");
                header.put("task_id", "");
                header.put("namespace", "SpeechTranscriber");
                header.put("name", "StartTranscription");
                header.put("appkey", appKey);
                startMsg.put("header", header);

                JSONObject payload = new JSONObject();
                payload.put("format", "pcm");
                payload.put("sample_rate", 8000);
                payload.put("enable_intermediate_result", true);
                payload.put("enable_punctuation_prediction", true);
                startMsg.put("payload", payload);

                webSocket.sendText(startMsg.toJSONString(), true);
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                String msg = data.toString();
                log.debug("阿里云 ASR 消息: {}", msg);
                handleAsrMessage(session.sessionId, msg);
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("阿里云 ASR WebSocket 错误", error);
            }
        };

        client.newWebSocketBuilder()
                .header("X-NLS-Token", token)
                .buildAsync(URI.create(wsUrl), listener);
    }

    private void handleAsrMessage(String sessionId, String msg) {
        try {
            JSONObject json = JSON.parseObject(msg);
            JSONObject header = json.getJSONObject("header");
            if (header == null) return;

            String name = header.getString("name");
            JSONObject payload = json.getJSONObject("payload");

            if ("SentenceEnd".equals(name) && payload != null) {
                String text = payload.getString("result");
                if (text != null && !text.isEmpty() && callback != null) {
                    log.info("ASR 识别结果[{}]: {}", sessionId, text);
                    callback.onResult(sessionId, text, true);
                }
            } else if ("TranscriptionResultChanged".equals(name) && payload != null) {
                String text = payload.getString("result");
                if (text != null && !text.isEmpty() && callback != null) {
                    callback.onResult(sessionId, text, false);
                }
            } else if ("TranscriptionStarted".equals(name)) {
                sessions.get(sessionId).taskId = header.getString("task_id");
                sessions.get(sessionId).ready = true;
                log.info("ASR 识别已就绪: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("解析 ASR 消息失败", e);
        }
    }

    private static class AliyunAsrSession {
        String sessionId;
        String taskId;
        WebSocket webSocket;
        boolean ready = false;
    }
}
