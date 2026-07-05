package com.changping.parking.controller;

import com.changping.parking.dialog.DialogManager;
import com.changping.parking.dialog.IntentParser;
import com.changping.parking.dialog.RagService;
import com.changping.parking.knowledge.ParkingInfoService;
import com.changping.parking.model.CallSession;
import com.changping.parking.model.CallSessionManager;
import com.changping.parking.model.DialogState;
import com.changping.parking.model.ParkingInfo;
import com.changping.parking.service.CallRecordService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试控制器
 * 
 * 提供 REST API 用于测试和调试系统功能，包括：
 * - 停车场数据查询
 * - 意图匹配测试
 * - RAG 问答测试
 * - 对话流程模拟
 * - 会话状态查询
 * 
 * @author Changping Parking AI Team
 */
@RestController
@RequestMapping("/api")
public class TestController {

    /** 对话管理器 */
    private final DialogManager dialogManager;

    /** 意图解析器 */
    private final IntentParser intentParser;

    /** RAG 服务 */
    private final RagService ragService;

    /** 停车场信息服务 */
    private final ParkingInfoService parkingInfoService;

    /** 会话管理器 */
    private final CallSessionManager sessionManager;

    /** 通话记录服务 */
    private final CallRecordService callRecordService;

    /**
     * 构造函数
     */
    public TestController(DialogManager dialogManager,
                          IntentParser intentParser,
                          RagService ragService,
                          ParkingInfoService parkingInfoService,
                          CallSessionManager sessionManager,
                          CallRecordService callRecordService) {
        this.dialogManager = dialogManager;
        this.intentParser = intentParser;
        this.ragService = ragService;
        this.parkingInfoService = parkingInfoService;
        this.sessionManager = sessionManager;
        this.callRecordService = callRecordService;
    }

    /**
     * 获取所有停车场列表
     * 
     * @return 停车场列表
     */
    @GetMapping("/parking/list")
    public List<ParkingInfo> listParking() {
        return parkingInfoService.getAll();
    }

    /**
     * 根据 ID 获取停车场信息
     * 
     * @param id 停车场 ID
     * @return 停车场信息
     */
    @GetMapping("/parking/{id}")
    public ParkingInfo getParking(@PathVariable String id) {
        return parkingInfoService.getById(id);
    }

    /**
     * 测试意图匹配
     * 
     * 输入文本，测试意图解析器是否能正确识别停车场名称。
     * 
     * @param text 用户输入文本
     * @return 匹配结果（包含输入文本、匹配到的停车场 ID 和名称）
     */
    @GetMapping("/intent/match")
    public Map<String, Object> matchParking(@RequestParam String text) {
        Map<String, Object> result = new HashMap<>();
        String parkingId = intentParser.matchParking(text);
        result.put("input", text);
        result.put("parkingId", parkingId);
        result.put("parkingName", intentParser.getParkingName(parkingId));
        return result;
    }

    /**
     * 测试 RAG 问答
     * 
     * 根据停车场 ID 和用户问题，测试 RAG 服务的回答生成能力。
     * 
     * @param parkingId 停车场 ID
     * @param question 用户问题
     * @return 问答结果（包含停车场 ID、问题和回答）
     */
    @GetMapping("/rag/ask")
    public Map<String, Object> ask(@RequestParam String parkingId, @RequestParam String question) {
        Map<String, Object> result = new HashMap<>();
        result.put("parkingId", parkingId);
        result.put("question", question);
        result.put("answer", ragService.generateAnswer(parkingId, question));
        return result;
    }

    /**
     * 开始对话
     * 
     * 创建一个新的对话会话，返回会话 ID 和欢迎语。
     * 
     * @return 会话信息（包含会话 ID 和欢迎语）
     */
    @PostMapping("/dialog/start")
    public Map<String, Object> startDialog() {
        String sessionId = UUID.randomUUID().toString();
        CallSession session = sessionManager.createSession(sessionId);
        session.transitionTo(DialogState.IDENTIFYING_PARK);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("welcome", "您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？");
        return result;
    }

    /**
     * 发送消息
     * 
     * 发送用户消息到对话系统，返回系统回复。
     * 
     * @param request 对话请求（包含会话 ID 和用户消息）
     * @return 对话结果（包含会话 ID、状态、回答、当前停车场信息）
     */
    @PostMapping("/dialog/send")
    public Map<String, Object> sendMessage(@RequestBody DialogRequest request) {
        String sessionId = request.getSessionId();
        String text = request.getText();

        CallSession session = sessionManager.getSession(sessionId);
        Map<String, Object> result = new HashMap<>();

        if (session == null) {
            result.put("error", "会话不存在");
            return result;
        }

        session.addUserMessage(text);

        String answer = processDialog(session, text);
        session.addBotMessage(answer);

        result.put("sessionId", sessionId);
        result.put("state", session.getState());
        result.put("answer", answer);
        result.put("currentParkingId", session.getCurrentParkingId());
        result.put("currentParkingName", session.getCurrentParkingName());

        return result;
    }

    /**
     * 处理对话流程
     *
     * 根据当前对话状态，分发给不同的处理方法。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String processDialog(CallSession session, String text) {
        switch (session.getState()) {
            case IDENTIFYING_PARK:
                return handleIdentifyingPark(session, text);
            case PARK_CONFIRMATION:
                return handleParkConfirmation(session, text);
            case QA_LOOP:
                return handleQaLoop(session, text);
            default:
                return "会话已结束。";
        }
    }

    /**
     * 处理问答循环阶段
     *
     * 用户可以自由提问关于停车场的问题。如果用户说再见或结束通话，则结束对话。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleQaLoop(CallSession session, String text) {
        if (isGoodbye(text)) {
            session.transitionTo(DialogState.END);
            return "感谢您的来电，祝您停车愉快，再见！";
        }
        return ragService.generateAnswer(session.getCurrentParkingId(), text);
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
        String[] goodbyes = {"再见", "拜拜", "没有问题了", "没问题了", "好了就这样", "就这样", "没了", "没有了", "谢谢", "感谢"};
        for (String g : goodbyes) {
            if (text.contains(g)) return true;
        }
        return false;
    }

    /**
     * 处理停车场识别阶段
     *
     * 使用意图解析器匹配用户所说的停车场名称。
     * 如果用户询问停车场列表，则返回列表供选择。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleIdentifyingPark(CallSession session, String text) {
        // 检查用户是否在询问停车场列表
        if (isAskingForList(text)) {
            return getParkingListMessage();
        }

        String parkingId = intentParser.matchParking(text);
        String parkingName = intentParser.getParkingName(parkingId);

        if (parkingId != null) {
            session.setPendingParkingId(parkingId);
            session.setPendingParkingName(parkingName);
            session.transitionTo(DialogState.PARK_CONFIRMATION);
            return String.format("您想咨询的是%s对吗？", parkingName);
        } else {
            return "抱歉，我没有听清您说的停车场名称。请您再说一遍好吗？比如西关环岛停车场或者体育馆停车场。";
        }
    }

    /**
     * 判断用户是否在询问停车场列表
     *
     * @param text 用户输入文本
     * @return true 表示询问列表
     */
    private boolean isAskingForList(String text) {
        String[] listKeywords = {"有哪些", "所有停车场", " list", "都有", "都有哪些", "多少个停车场"};
        for (String keyword : listKeywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 获取停车场列表消息
     *
     * @return 停车场列表格式化消息
     */
    private String getParkingListMessage() {
        List<ParkingInfo> allParking = parkingInfoService.getAll();
        StringBuilder sb = new StringBuilder("昌平区共有以下停车场：");
        for (ParkingInfo p : allParking) {
            sb.append(String.format("%s（%s）、", p.getName(), p.getAddress()));
        }
        // 去掉最后一个顿号
        String result = sb.toString();
        if (result.endsWith("、")) {
            result = result.substring(0, result.length() - 1);
        }
        result += "。请问您想查询哪个停车场？";
        return result;
    }

    /**
     * 处理停车场确认阶段
     *
     * 判断用户是否确认选中的停车场。
     *
     * @param session 通话会话
     * @param text 用户输入文本
     * @return 系统回复
     */
    private String handleParkConfirmation(CallSession session, String text) {
        // 优先检查否定，避免"不是"被误判为肯定
        boolean denied = text.equals("不") || text.equals("不是") || text.equals("不对") ||
                        text.equals("不是的") || text.contains("不是") || text.contains("不对");
        boolean confirmed = text.equals("是") || text.equals("是的") || text.equals("对") ||
                           text.equals("没错") || text.equals("嗯") || text.equals("嗯嗯");

        if (denied) {
            session.setPendingParkingId(null);
            session.setPendingParkingName(null);
            session.transitionTo(DialogState.IDENTIFYING_PARK);
            return "抱歉，那请问您想查询哪个停车场呢？";
        } else if (confirmed) {
            session.setCurrentParkingId(session.getPendingParkingId());
            session.setCurrentParkingName(session.getPendingParkingName());
            session.setPendingParkingId(null);
            session.setPendingParkingName(null);
            session.transitionTo(DialogState.QA_LOOP);
            return String.format("好的，已为您接入%s的智能咨询服务。您可以问我关于营业时间、收费标准、剩余车位等问题。",
                    session.getCurrentParkingName());
        } else {
            return "请问是这个停车场吗？请说是或者不是。";
        }
    }

    /**
     * 获取所有会话状态
     * 
     * @return 会话统计信息（包含会话数量和所有会话列表）
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        Map<String, Object> result = new HashMap<>();
        result.put("count", sessionManager.getActiveSessionCount());
        result.put("sessions", sessionManager.getAllSessions());
        return result;
    }

    /**
     * 结束指定会话并保存通话记录（测试用）
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @PostMapping("/dialog/end-test")
    public Map<String, Object> endSessionForTest(@RequestParam String sessionId) {
        Map<String, Object> result = new HashMap<>();
        CallSession session = sessionManager.getSession(sessionId);

        if (session == null) {
            result.put("success", false);
            result.put("message", "会话不存在");
            return result;
        }

        // 将会话标记为结束状态
        session.transitionTo(DialogState.END);

        // 保存通话记录
        callRecordService.saveRecord(session);

        // 删除会话
        sessionManager.removeSession(sessionId);

        result.put("success", true);
        result.put("message", "通话记录已保存");
        result.put("sessionId", sessionId);
        return result;
    }

    /**
     * 查询通话记录列表（测试用）
     *
     * @return 通话记录列表
     */
    @GetMapping("/call-records")
    public Map<String, Object> listCallRecords() {
        Map<String, Object> result = new HashMap<>();
        result.put("count", callRecordService.countAll());
        result.put("records", callRecordService.findAll());
        return result;
    }

    /**
     * 对话请求对象
     */
    @Data
    public static class DialogRequest {
        /** 会话 ID */
        private String sessionId;
        /** 用户消息 */
        private String text;
    }
}
