package com.changping.parking.controller;

import com.changping.parking.speech.TtsService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 语音对话控制器
 * 
 * 提供语音合成和语音对话相关的 REST API 接口，支持前端通过浏览器麦克风进行语音交互。
 * 
 * @author Changping Parking AI Team
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    /** TTS 服务 */
    private final TtsService ttsService;

    /**
     * 构造函数
     * 
     * @param ttsService TTS 服务实例
     */
    public VoiceController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    /**
     * 文本转语音接口
     * 
     * 将文本合成为语音音频文件，并返回给前端播放。
     * 
     * @param text 要合成的文本内容
     * @param sessionId 会话ID（可选）
     * @return WAV 格式音频文件
     */
    @GetMapping("/tts")
    public ResponseEntity<Resource> textToSpeech(
            @RequestParam String text,
            @RequestParam(required = false) String sessionId) {
        
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String audioFilePath = ttsService.synthesize(sessionId, text);
            
            if (audioFilePath == null) {
                return ResponseEntity.notFound().build();
            }

            File audioFile = new File(audioFilePath);
            if (!audioFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(audioFile);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + audioFile.getName() + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试语音合成接口
     * 
     * 用于测试 TTS 服务是否正常工作。
     * 
     * @param text 测试文本
     * @return 合成结果
     */
    @GetMapping("/tts/test")
    public Map<String, Object> testTts(@RequestParam String text) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String audioFilePath = ttsService.synthesize("test", text);
            
            if (audioFilePath != null) {
                result.put("success", true);
                result.put("message", "语音合成成功");
                result.put("filePath", audioFilePath);
                result.put("fileExists", new File(audioFilePath).exists());
            } else {
                result.put("success", false);
                result.put("message", "语音合成失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "语音合成异常: " + e.getMessage());
        }
        
        return result;
    }
}