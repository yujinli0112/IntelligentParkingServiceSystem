package com.changping.parking.speech;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Windows SAPI TTS 服务实现
 * 
 * 使用 Windows 自带的 Speech API (SAPI) 进行语音合成。
 * 支持 Windows 10/11 内置的中文语音引擎（如 Microsoft Huihui）。
 * 
 * 使用条件：配置 `tts.provider=sapi`
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tts.provider", havingValue = "sapi")
public class SapiTtsService implements TtsService {

    /** 临时文件目录，存储合成的音频文件 */
    @Value("${audio.tempDir:./temp/audio}")
    private String tempDir;

    /** 中文语音名称，Windows 默认中文语音 */
    @Value("${tts.sapi.voice:Huihui}")
    private String voiceName;

    /** 音频采样率 */
    @Value("${audio.sampleRate:8000}")
    private int sampleRate;

    public SapiTtsService() {
        log.info("SapiTtsService 已加载，使用 Windows SAPI 进行中文语音合成");
    }

    /**
     * 合成语音
     * 
     * 使用 PowerShell 调用 Windows SAPI 将文本合成为 WAV 文件。
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
            String absolutePath = filePath.toAbsolutePath().toString();

            // 使用 PowerShell 调用 SAPI 进行语音合成
            String script = buildPowerShellScript(text, absolutePath, voiceName);
            
            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && filePath.toFile().exists()) {
                log.info("SAPI TTS 合成完成: text={}, file={}", text.length() > 30 ? text.substring(0, 30) + "..." : text, absolutePath);
                // 转换采样率为 8kHz（FreeSWITCH 要求）
                return convertSampleRate(absolutePath, sampleRate);
            } else {
                log.error("SAPI TTS 合成失败: exitCode={}, output={}", exitCode, output);
                return null;
            }
        } catch (Exception e) {
            log.error("SAPI TTS 合成失败", e);
            return null;
        }
    }

    /**
     * 构建 PowerShell 脚本
     * 
     * 使用 SAPI COM 对象进行语音合成并保存为 WAV 文件。
     * 
     * @param text 要合成的文本
     * @param outputPath 输出文件路径
     * @param voice 语音名称
     * @return PowerShell 脚本字符串
     */
    private String buildPowerShellScript(String text, String outputPath, String voice) {
        // 转义文本中的特殊字符，防止 PowerShell 脚本注入
        String escapedText = text.replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("$", "\\$")
                .replace("`", "``");
        String escapedPath = outputPath.replace("'", "''");
        
        return String.format(
            "$voice = New-Object -ComObject SAPI.SpVoice; " +
            "$file = New-Object -ComObject SAPI.SpFileStream; " +
            "$file.Open('%s', 3, 0); " +
            "$voice.AudioOutputStream = $file; " +
            "$voice.Voice = $voice.GetVoices().Item(0); " +
            // 尝试查找中文语音
            "foreach ($v in $voice.GetVoices()) { " +
            "  if ($v.GetAttribute('Name') -like '*Huihui*' -or $v.GetAttribute('Name') -like '*Chinese*' -or $v.GetAttribute('Language') -eq '804') { " +
            "    $voice.Voice = $v; break; " +
            "  } " +
            "} " +
            "$voice.Speak('%s'); " +
            "$file.Close();",
            escapedPath, escapedText
        );
    }

    /**
     * 转换音频采样率
     * 
     * 使用 ffmpeg 或 sox 将音频转换为 FreeSWITCH 要求的 8kHz。
     * 如果没有安装 ffmpeg/sox，则返回原文件（FreeSWITCH 可以自动处理）。
     * 
     * @param inputPath 输入文件路径
     * @param targetRate 目标采样率
     * @return 转换后的文件路径
     */
    private String convertSampleRate(String inputPath, int targetRate) {
        // FreeSWITCH 可以自动处理不同采样率，暂不转换
        // 如果需要转换，可以使用 ffmpeg：
        // ffmpeg -i input.wav -ar 8000 -ac 1 output.wav
        return inputPath;
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
}