package com.changping.parking.speech;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
@ConditionalOnProperty(name = "tts.provider", havingValue = "aliyun")
public class AliyunTtsService implements TtsService {

    @Value("${tts.aliyun.appKey}")
    private String appKey;

    @Value("${tts.aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${tts.aliyun.accessKeySecret}")
    private String accessKeySecret;

    @Value("${tts.aliyun.voice:xiaoyun}")
    private String voice;

    @Value("${tts.aliyun.sampleRate:8000}")
    private int sampleRate;

    @Value("${audio.tempDir:./temp/audio}")
    private String tempDir;

    @Value("${tts.aliyun.url:https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts}")
    private String ttsUrl;

    @Override
    public String synthesize(String sessionId, String text) {
        try {
            Path dir = Paths.get(tempDir, sessionId);
            Files.createDirectories(dir);

            String fileName = "tts_" + System.currentTimeMillis() + ".wav";
            Path filePath = dir.resolve(fileName);

            byte[] audioData = requestTts(text);
            if (audioData != null && audioData.length > 0) {
                saveWavFile(audioData, filePath.toFile());
                log.info("阿里云 TTS 合成完成: text={}, file={}", text, filePath.toAbsolutePath());
                return filePath.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            log.error("阿里云 TTS 合成失败", e);
        }
        return null;
    }

    @Override
    public String getTempDir() {
        return tempDir;
    }

    private byte[] requestTts(String text) {
        try {
            JSONObject request = new JSONObject();
            request.put("appkey", appKey);
            request.put("text", text);
            request.put("token", generateToken());
            request.put("format", "wav");
            request.put("sample_rate", sampleRate);
            request.put("voice", voice);
            request.put("volume", 50);
            request.put("speech_rate", 1.0);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ttsUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(request.toJSONString()))
                    .build();

            java.net.http.HttpResponse<byte[]> response = client.send(httpRequest,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("audio")) {
                return response.body();
            } else {
                log.error("TTS 请求失败: {}", new String(response.body()));
                return null;
            }
        } catch (Exception e) {
            log.error("调用阿里云 TTS 失败", e);
            return null;
        }
    }

    private String generateToken() {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String signatureNonce = String.valueOf(System.currentTimeMillis());

            String stringToSign = accessKeyId + signatureNonce + timestamp;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    accessKeySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signBytes);

            return signature + "&" + accessKeyId + "&" + signatureNonce + "&" + timestamp;
        } catch (Exception e) {
            log.error("生成阿里云 Token 失败", e);
            return "";
        }
    }

    private void saveWavFile(byte[] audioData, File file) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
             AudioInputStream ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }
}
