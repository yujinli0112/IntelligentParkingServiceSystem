package com.changping.parking.model;

import lombok.Data;
import io.netty.channel.Channel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 通话会话实体类
 * 
 * 存储单路通话的完整状态信息，包括当前对话状态、选中的停车场、对话历史等。
 * 每个通话建立时创建一个实例，通话结束时销毁。
 * 
 * @author Changping Parking AI Team
 */
@Data
public class CallSession {

    /** 会话唯一标识，由 FreeSWITCH 生成或 UUID 随机生成 */
    private String sessionId;

    /** 主叫号码 */
    private String callerNumber;

    /** 被叫号码（如 123456） */
    private String calledNumber;

    /** Netty 通道引用，用于发送命令到 FreeSWITCH */
    private Channel channel;

    /** 当前对话状态 */
    private DialogState state;

    /** 当前选中的停车场 ID */
    private String currentParkingId;

    /** 当前选中的停车场名称 */
    private String currentParkingName;

    /** 对话历史记录，格式为 "用户: xxx" 或 "客服: xxx"，最多保留 50 条 */
    private List<String> conversationHistory;

    /** 对话历史最大保留条数 */
    private static final int MAX_HISTORY_SIZE = 50;

    /** 会话开始时间 */
    private LocalDateTime startTime;

    /** 最后活动时间，用于超时检测 */
    private LocalDateTime lastActivityTime;

    /** ASR 是否已启动 */
    private boolean asrStarted;

    /** 待确认的停车场 ID（在确认阶段使用） */
    private String pendingParkingId;

    /** 待确认的停车场名称（在确认阶段使用） */
    private String pendingParkingName;

    /** 当前录音文件路径（用于录音式对话） */
    private String recordFilePath;

    /**
     * 默认构造函数
     * 初始化状态为 WAITING_WELCOME，创建空的对话历史列表
     */
    public CallSession() {
        this.state = DialogState.WAITING_WELCOME;
        this.conversationHistory = new ArrayList<>();
        this.startTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.asrStarted = false;
    }

    /**
     * 添加用户消息到对话历史
     * 
     * @param message 用户消息内容
     */
    public void addUserMessage(String message) {
        if (conversationHistory.size() >= MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
        }
        conversationHistory.add("用户: " + message);
        lastActivityTime = LocalDateTime.now();
    }

    /**
     * 添加客服回复到对话历史
     * 
     * @param message 客服回复内容
     */
    public void addBotMessage(String message) {
        if (conversationHistory.size() >= MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
        }
        conversationHistory.add("客服: " + message);
        lastActivityTime = LocalDateTime.now();
    }

    /**
     * 状态转换
     * 
     * @param newState 新的对话状态
     */
    public void transitionTo(DialogState newState) {
        this.state = newState;
    }
}
