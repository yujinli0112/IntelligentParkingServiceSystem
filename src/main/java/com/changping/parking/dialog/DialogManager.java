package com.changping.parking.dialog;

import com.changping.parking.model.CallSession;
import com.changping.parking.model.CallSessionManager;
import com.changping.parking.model.DialogState;
import com.changping.parking.service.CallRecordService;
import com.changping.parking.speech.AsrService;
import com.changping.parking.tcp.AudioSocketByteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话管理器
 * 
 * 核心对话控制类，管理对话状态机的流转，协调 ASR/TTS/RAG 各组件。
 * 根据当前对话状态，处理用户输入并生成相应的回复。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Component
public class DialogManager {

    /** 会话管理器，管理所有通话会话 */
    private final CallSessionManager sessionManager;

    /** 音频 Socket 处理器，用于发送播放/挂断命令 */
    private final AudioSocketByteHandler audioSocketByteHandler;

    /** ASR 服务，接收识别结果回调 */
    private final AsrService asrService;

    /** 意图解析器，用于识别用户所说的停车场名称 */
    private final IntentParser intentParser;

    /** RAG 服务，用于生成停车场相关问题的回答 */
    private final RagService ragService;

    /** 通话记录服务，用于保存通话记录 */
    private final CallRecordService callRecordService;

    /** 线程池，用于异步挂断等操作 */
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("dialog-async-" + t.getId());
        return t;
    });

    // ========== 系统提示文本（从配置读取） ==========
    @Value("${system.welcome:您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？}")
    private String welcomeText;

    @Value("${system.retryParking:抱歉，我没有听清您说的停车场名称。请您再说一遍好吗？}")
    private String retryParkingText;

    @Value("${system.confirmParking:您想咨询的是%s对吗？}")
    private String confirmParkingText;

    @Value("${system.parkIntro:好的，已为您接入%s的智能咨询服务。您可以问我关于营业时间、收费标准、剩余车位等问题。}")
    private String parkIntroText;

    @Value("${system.denyParking:抱歉，那请问您想查询哪个停车场呢？}")
    private String denyParkingText;

    @Value("${system.clarifyParking:请问是这个停车场吗？请说是或者不是。}")
    private String clarifyParkingText;

    @Value("${system.goodbye:感谢您的来电，祝您停车愉快，再见！}")
    private String goodbyeText;

    @Value("${system.exampleParking:比如西关环岛停车场或者体育馆停车场}")
    private String exampleParkingText;

    @Value("${system.notFoundParking:抱歉，没有找到您说的这个停车场。请问您想查询哪个停车场呢？}")
    private String notFoundParkingText;

    @Value("${system.switchParking:好的，请问您想查询哪个停车场呢？}")
    private String switchParkingText;

    /**
     * 构造函数
     * 
     * @param sessionManager 会话管理器
     * @param audioSocketByteHandler 音频 Socket 处理器
     * @param asrService ASR 服务
     * @param intentParser 意图解析器
     * @param ragService RAG 服务
     * @param callRecordService 通话记录服务
     */
    public DialogManager(CallSessionManager sessionManager,
                         AudioSocketByteHandler audioSocketByteHandler,
                         AsrService asrService,
                         IntentParser intentParser,
                         RagService ragService,
                         CallRecordService callRecordService) {
        this.sessionManager = sessionManager;
        this.audioSocketByteHandler = audioSocketByteHandler;
        this.asrService = asrService;
        this.intentParser = intentParser;
        this.ragService = ragService;
        this.callRecordService = callRecordService;
    }

    /**
     * 初始化方法，注册 ASR 结果回调
     */
    @PostConstruct
    public void init() {
        asrService.setResultCallback(this::handleAsrResult);
    }

    /**
     * 处理欢迎流程
     * 
     * 播放欢迎语，询问用户想查询哪个停车场，状态转换为 IDENTIFYING_PARK。
     * 
     * @param session 通话会话
     */
    public void handleWelcome(CallSession session) {
        log.info("[{}] 播放欢迎语", session.getSessionId());
        session.addBotMessage(welcomeText);
        session.transitionTo(DialogState.IDENTIFYING_PARK);
        audioSocketByteHandler.playAudioInline(session.getSessionId(), welcomeText);
    }

    /**
     * 处理 ASR 识别结果
     * 
     * 根据当前对话状态，分发给不同的处理方法。
     * 
     * @param sessionId 会话 ID
     * @param text 识别出的文本
     * @param isFinal 是否为最终结果（false 表示中间结果，暂不处理）
     */
    public void handleAsrResult(String sessionId, String text, boolean isFinal) {
        if (!isFinal) {
            return;
        }

        CallSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            log.warn("收到未知会话: {}", sessionId);
            return;
        }

        session.addUserMessage(text);
        log.info("[{}] 用户说: {}", sessionId, text);

        switch (session.getState()) {
            case IDENTIFYING_PARK:
                handleIdentifyingPark(session, text);
                break;
            case PARK_CONFIRMATION:
                handleParkConfirmation(session, text);
                break;
            case QA_LOOP:
                handleQaLoop(session, text);
                break;
            default:
                log.warn("未知状态: {}", session.getState());
        }
    }

    /**
     * 处理停车场识别阶段
     *
     * 使用意图解析器匹配用户所说的停车场名称，如果匹配成功则进入确认阶段，
     * 否则提示用户重新输入。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleIdentifyingPark(CallSession session, String text) {
        // 先检测再见，避免用户说"哪个都不想查询再见"时被误判为没听清
        if (isGoodbye(text)) {
            return handleEnd(session);
        }

        String parkingId = intentParser.matchParking(text);
        String parkingName = intentParser.getParkingName(parkingId);

        if (parkingId != null) {
            session.setPendingParkingId(parkingId);
            session.setPendingParkingName(parkingName);
            session.transitionTo(DialogState.PARK_CONFIRMATION);

            String confirmText = String.format(confirmParkingText, parkingName);
            session.addBotMessage(confirmText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), confirmText);
            return confirmText;
        } else {
            // 区分"没听清"和"找不到"：用户提到停车相关关键词则说明是找不到该停车场
            boolean mentionsParking = containsParkingKeyword(text);
            String retryText = mentionsParking ? notFoundParkingText : (retryParkingText + exampleParkingText);
            session.addBotMessage(retryText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), retryText);
            return retryText;
        }
    }

    /**
     * 处理停车场确认阶段
     *
     * 判断用户是否确认选中的停车场，如果确认则进入问答阶段，
     * 如果否认则回到识别阶段重新输入。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleParkConfirmation(CallSession session, String text) {
        // 先检测否定（"不是"也包含"是"字，必须先排除）
        boolean denied = isNegative(text);
        boolean confirmed = !denied && isAffirmative(text);

        if (confirmed) {
            session.setCurrentParkingId(session.getPendingParkingId());
            session.setCurrentParkingName(session.getPendingParkingName());
            session.setPendingParkingId(null);
            session.setPendingParkingName(null);
            session.transitionTo(DialogState.QA_LOOP);

            String confirmText = String.format(parkIntroText, session.getCurrentParkingName());
            session.addBotMessage(confirmText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), confirmText);
            return confirmText;
        } else if (denied) {
            session.setPendingParkingId(null);
            session.setPendingParkingName(null);
            session.transitionTo(DialogState.IDENTIFYING_PARK);

            session.addBotMessage(denyParkingText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), denyParkingText);
            return denyParkingText;
        } else {
            session.addBotMessage(clarifyParkingText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), clarifyParkingText);
            return clarifyParkingText;
        }
    }

    /**
     * 处理问答循环阶段
     *
     * 用户可以自由提问关于停车场的问题，使用 RAG 服务生成回答。
     * 如果用户说再见，则结束对话。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleQaLoop(CallSession session, String text) {
        if (isGoodbye(text)) {
            return handleEnd(session);
        }

        // 检测用户是否提到了另一个停车场名称（直接匹配优先级最高）
        String otherParkingId = intentParser.matchParking(text);
        if (otherParkingId != null && !otherParkingId.equals(session.getCurrentParkingId())) {
            String otherParkingName = intentParser.getParkingName(otherParkingId);
            session.setCurrentParkingId(otherParkingId);
            session.setCurrentParkingName(otherParkingName);
            String answer = ragService.generateAnswer(otherParkingId, text);
            session.addBotMessage(answer);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), answer);
            return answer;
        }

        // 检测用户是否想切换停车场（未指定具体名称，如"换一个"）
        if (wantsSwitchParking(text)) {
            session.setCurrentParkingId(null);
            session.setCurrentParkingName(null);
            session.transitionTo(DialogState.IDENTIFYING_PARK);
            session.addBotMessage(switchParkingText);
            audioSocketByteHandler.playAudioInline(session.getSessionId(), switchParkingText);
            return switchParkingText;
        }

        String answer = ragService.generateAnswer(session.getCurrentParkingId(), text);
        session.addBotMessage(answer);
        audioSocketByteHandler.playAudioInline(session.getSessionId(), answer);
        return answer;
    }

    /**
     * 处理对话结束
     *
     * 播放结束语，保存通话记录，3秒后挂断通话。
     *
     * @param session 通话会话
     * @return 结束语
     */
    public String handleEnd(CallSession session) {
        session.transitionTo(DialogState.END);
        session.addBotMessage(goodbyeText);
        audioSocketByteHandler.playAudioInline(session.getSessionId(), goodbyeText);

        callRecordService.saveRecord(session);

        executorService.submit(() -> {
            try {
                Thread.sleep(3000);
                audioSocketByteHandler.hangup(session.getSessionId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        return goodbyeText;
    }

    /**
     * 处理文本消息（用于 WebSocket）
     *
     * @param sessionId 会话 ID
     * @param text 用户输入文本
     * @return 系统回复
     */
    public String processTextMessage(String sessionId, String text) {
        CallSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return "会话不存在";
        }

        session.addUserMessage(text);

        switch (session.getState()) {
            case IDENTIFYING_PARK:
                return handleIdentifyingPark(session, text);
            case PARK_CONFIRMATION:
                return handleParkConfirmation(session, text);
            case QA_LOOP:
                return handleQaLoop(session, text);
            case END:
                return "会话已结束";
            default:
                return "未知状态";
        }
    }

    /**
     * 处理音频消息（用于 WebSocket）
     *
     * @param sessionId 会话 ID
     * @param audioBase64 音频数据（Base64 编码）
     */
    public void processAudioMessage(String sessionId, String audioBase64) {
        byte[] audioData = java.util.Base64.getDecoder().decode(audioBase64);
        asrService.feedAudio(sessionId, audioData);
    }

    /**
     * 判断是否为肯定回答
     * 
     * @param text 用户输入文本
     * @return true 表示肯定，false 表示否定或不确定
     */
    private boolean isAffirmative(String text) {
        String[] affirmatives = {"是", "对", "是的", "对的", "嗯", "嗯嗯", "没错", "就是它", "就是这个"};
        for (String a : affirmatives) {
            if (text.contains(a)) return true;
        }
        return false;
    }

    /**
     * 判断是否为否定回答
     * 
     * @param text 用户输入文本
     * @return true 表示否定，false 表示肯定或不确定
     */
    private boolean isNegative(String text) {
        String[] negatives = {"不是", "不对", "不", "不是的", "不对的", "错了", "不是这个"};
        for (String n : negatives) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    /**
     * 判断是否为结束语
     *
     * @param text 用户输入文本
     * @return true 表示结束语，false 表示继续对话
     */
    private boolean isGoodbye(String text) {
        // 优先检测明确的结束意图
        String[] endKeywords = {"结束通话", "通话结束", "挂断", "挂了吧", "挂了", "挂电话"};
        for (String k : endKeywords) {
            if (text.contains(k)) return true;
        }

        // 检测告别语
        String[] goodbyes = {"再见", "拜拜", "没有问题了", "没问题了", "没啥问题了", "没啥问题", "没啥了", "好了就这样", "就这样", "没了", "没有了", "谢谢", "感谢", "好的", "可以了"};
        for (String g : goodbyes) {
            if (text.contains(g)) return true;
        }
        return false;
    }

    /**
     * 判断用户输入是否包含停车相关关键词
     * 用于区分"没听清"和"停车场不存在"两种场景
     */
    private boolean containsParkingKeyword(String text) {
        String[] keywords = {"停车场", "停车", "车位", "parking", "车库"};
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    /**
     * 判断用户是否想切换停车场
     */
    private boolean wantsSwitchParking(String text) {
        String[] keywords = {
            "其他停车场", "别的停车场", "另一个停车场", "换一个停车场", "换停车场",
            "切换停车场", "换一家", "其他停车", "别的停车",
            "其他家", "另一家", "换一个地方"
        };
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
