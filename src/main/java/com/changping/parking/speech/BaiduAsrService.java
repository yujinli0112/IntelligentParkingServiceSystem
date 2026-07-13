package com.changping.parking.speech;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 百度语音识别服务
 * 使用百度短语音识别 REST API，支持中文普通话识别
 * 免费额度：5万次/天
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "asr.provider", havingValue = "baidu")
public class BaiduAsrService implements AsrService {

    @Value("${asr.baidu.apiKey}")
    private String apiKey;

    @Value("${asr.baidu.secretKey}")
    private String secretKey;

    @Value("${asr.baidu.appId:}")
    private String appId;

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";

    private AsrResultCallback callback;
    private final Map<String, BaiduAsrSession> sessions = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String accessToken;
    private long tokenExpireTime = 0;

    private static class BaiduAsrSession {
        volatile boolean started = false;
    }

    @PostConstruct
    public void init() {
        refreshToken();
    }

    @Override
    public void startSession(String sessionId) {
        sessions.put(sessionId, new BaiduAsrSession());
        log.info("百度 ASR 会话开始: {}", sessionId);
    }

    @Override
    public void feedAudio(String sessionId, byte[] audioData) {
        BaiduAsrSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        try {
            session.started = true;

            // 只发送本次收到的音频数据，不累积历史音频
            String text = recognizeAudio(audioData);

            if (text != null && !text.isEmpty() && callback != null) {
                log.info("百度 ASR 识别结果[{}]: {}", sessionId, text);
                callback.onResult(sessionId, text, true);
            }

        } catch (Exception e) {
            log.error("百度 ASR 识别失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public void stopSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("百度 ASR 会话结束: {}", sessionId);
    }

    @Override
    public void setResultCallback(AsrResultCallback callback) {
        this.callback = callback;
    }

    /**
     * 识别 PCM 音频数据
     * 将 48000Hz PCM 降采样到 16000Hz，发送到百度 ASR
     */
    private String recognizeAudio(byte[] pcmData) {
        try {
            // 确保 token 有效
            if (System.currentTimeMillis() > tokenExpireTime) {
                refreshToken();
            }

            // 降采样 48000Hz -> 16000Hz (每3个采样点取1个)
            byte[] resampled = downsample48000To16000(pcmData);

            // Base64 编码
            String speech = Base64.getEncoder().encodeToString(resampled);

            // 构建请求 - 使用 dev_pid=1536 (标准普通话模型，含简单英文)
            JSONObject body = new JSONObject();
            body.put("format", "pcm");
            body.put("rate", 16000);
            body.put("channel", 1);
            body.put("cuid", "parking-ai-callcenter");
            body.put("token", accessToken);
            body.put("speech", speech);
            body.put("len", resampled.length);
            body.put("dev_pid", 1536); // 普通话(支持简单的英文识别)，免费配额更充足

            String jsonBody = body.toJSONString();
            log.info("百度 ASR 请求: format=pcm, rate=16000, len={}, dev_pid=1536", resampled.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ASR_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject result = JSON.parseObject(response.body());

            log.debug("百度 ASR 响应: {}", result.toJSONString());

            int errNo = result.getIntValue("err_no");
            if (errNo == 0) {
                return result.getJSONArray("result").getString(0);
            } else {
                int errCode = result.getIntValue("err_no");
                String errMsg = result.getString("err_msg");
                log.error("百度 ASR 返回错误: err_no={}, err_msg={}", errCode, errMsg);

                // 3305 可能是新账号需要先开通服务，给出明确提示
                if (errCode == 3305) {
                    log.error("请确认：1) 百度控制台已创建【语音识别】应用 2) 已领取免费资源包 3) 账号已实名认证");
                }
                return null;
            }

        } catch (Exception e) {
            log.error("百度 ASR 请求失败", e);
            return null;
        }
    }

    /**
     * 简单的降采样：48000Hz -> 16000Hz
     * 每3个采样点取平均值（16-bit PCM）
     */
    private byte[] downsample48000To16000(byte[] pcmData) {
        int ratio = 3; // 48000 / 16000 = 3
        int outputLen = (pcmData.length / 2) / ratio * 2; // 16-bit = 2 bytes per sample
        byte[] output = new byte[outputLen];

        for (int i = 0, j = 0; i < pcmData.length - (ratio * 2 - 1); i += ratio * 2, j += 2) {
            // 取3个采样点的平均值（16-bit little-endian）
            int sum = 0;
            for (int k = 0; k < ratio; k++) {
                int sample = (pcmData[i + k * 2 + 1] << 8) | (pcmData[i + k * 2] & 0xFF);
                sum += sample;
            }
            int avg = sum / ratio;
            output[j] = (byte) (avg & 0xFF);
            output[j + 1] = (byte) ((avg >> 8) & 0xFF);
        }
        return output;
    }

    /**
     * 获取百度 access_token
     */
    private synchronized void refreshToken() {
        try {
            String url = TOKEN_URL + "?grant_type=client_credentials"
                    + "&client_id=" + apiKey
                    + "&client_secret=" + secretKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject result = JSON.parseObject(response.body());

            this.accessToken = result.getString("access_token");
            int expiresIn = result.getIntValue("expires_in"); // 秒
            this.tokenExpireTime = System.currentTimeMillis() + (expiresIn - 600) * 1000; // 提前10分钟刷新

            log.info("百度 ASR Token 刷新成功, 有效期: {}秒", expiresIn);

        } catch (Exception e) {
            log.error("百度 ASR Token 刷新失败", e);
        }
    }
}