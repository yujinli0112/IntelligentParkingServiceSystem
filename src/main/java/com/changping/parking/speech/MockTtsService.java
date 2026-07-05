package com.changping.parking.speech;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Mock TTS 服务实现
 * 
 * 用于开发测试环境，模拟语音合成功能。
 * 将文本转换为静音的 WAV 文件（1秒），实际开发中可以通过 REST API 手动触发识别。
 * 
 * 使用条件：配置 `tts.provider=mock`（默认）
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tts.provider", havingValue = "mock", matchIfMissing = true)
public class MockTtsService implements TtsService {

    /** 临时文件目录，存储合成的音频文件 */
    @Value("${audio.tempDir:./temp/audio}")
    private String tempDir;

    /**
     * 合成语音
     * 
     * 将文本合成为静音的 WAV 文件（1秒），用于测试流程。
     * 
     * @param sessionId 会话唯一标识
     * @param text 要合成的文本
     * @return 音频文件的绝对路径，如果合成失败返回 null
     */
    @Override
    public String synthesize(String sessionId, String text) {
        try {
            Path dir = Paths.get(tempDir, sessionId);
            Files.createDirectories(dir);

            String fileName = "tts_" + System.currentTimeMillis() + ".wav";
            Path filePath = dir.resolve(fileName);

            generateSilenceWav(filePath.toFile(), 1);

            log.info("Mock TTS 合成完成: text={}, file={}", text, filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Mock TTS 合成失败", e);
            return null;
        }
    }

    /**
     * 获取临时文件目录
     * 
     * @return 临时文件目录路径
     */
    @Override
    public String getTempDir() {
        return tempDir;
    }

    /**
     * 生成静音 WAV 文件
     * 
     * 创建一个指定时长的静音 WAV 文件，格式为 8kHz, 16bit, 单声道。
     * 
     * @param file 目标文件
     * @param durationSeconds 时长（秒）
     * @throws IOException 如果写入文件失败
     */
    private void generateSilenceWav(File file, int durationSeconds) throws IOException {
        float sampleRate = 8000;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;

        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        int dataLength = (int) (sampleRate * durationSeconds * channels * (sampleSizeInBits / 8));
        byte[] audioData = new byte[dataLength];

        for (int i = 0; i < dataLength; i += 2) {
            audioData[i] = 0;
            audioData[i + 1] = 0;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream ais = new AudioInputStream(bais, format, dataLength / format.getFrameSize());

        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        ais.close();
    }
}
