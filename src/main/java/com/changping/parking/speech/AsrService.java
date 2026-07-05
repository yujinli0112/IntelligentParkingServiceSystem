package com.changping.parking.speech;

/**
 * ASR（语音识别）服务接口
 * 
 * 定义语音识别服务的基本操作，包括会话管理和音频数据处理。
 * 支持多种实现（Mock、阿里云等），通过配置文件选择。
 * 
 * @author Changping Parking AI Team
 */
public interface AsrService {

    /**
     * 启动语音识别会话
     * 
     * @param sessionId 会话唯一标识
     */
    void startSession(String sessionId);

    /**
     * 推送音频数据到识别服务
     * 
     * @param sessionId 会话唯一标识
     * @param audioData 音频字节数据（PCM 格式）
     */
    void feedAudio(String sessionId, byte[] audioData);

    /**
     * 停止语音识别会话
     * 
     * @param sessionId 会话唯一标识
     */
    void stopSession(String sessionId);

    /**
     * 设置识别结果回调
     * 
     * @param callback 回调接口实现
     */
    void setResultCallback(AsrResultCallback callback);

    /**
     * ASR 识别结果回调接口
     */
    interface AsrResultCallback {
        /**
         * 识别结果回调
         * 
         * @param sessionId 会话唯一标识
         * @param text 识别出的文本
         * @param isFinal 是否为最终结果（false 表示中间结果）
         */
        void onResult(String sessionId, String text, boolean isFinal);
    }
}
