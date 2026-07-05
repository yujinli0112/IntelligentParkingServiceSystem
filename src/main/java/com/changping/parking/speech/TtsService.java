package com.changping.parking.speech;

/**
 * TTS（语音合成）服务接口
 * 
 * 定义语音合成服务的基本操作，将文本转换为音频文件。
 * 支持多种实现（Mock、阿里云等），通过配置文件选择。
 * 
 * @author Changping Parking AI Team
 */
public interface TtsService {

    /**
     * 合成语音
     * 
     * 将文本合成为 WAV 格式音频文件，并返回文件路径。
     * 
     * @param sessionId 会话唯一标识
     * @param text 要合成的文本
     * @return 音频文件的绝对路径，如果合成失败返回 null
     */
    String synthesize(String sessionId, String text);

    /**
     * 获取临时文件目录
     * 
     * @return 临时文件目录路径
     */
    String getTempDir();
}
