package com.changping.parking.dialog;

import com.changping.parking.knowledge.ParkingInfoService;
import com.changping.parking.model.ParkingInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG（检索增强生成）服务
 * 
 * 根据用户问题和停车场信息生成回答，支持两种模式：
 * 1. 规则模式（mock）- 使用关键词匹配生成预设回答，适用于开发测试
 * 2. LLM 模式（deepseek/openai）- 使用大语言模型生成自然语言回答
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Service
public class RagService {

    /** 停车场信息服务，用于获取停车场数据 */
    private final ParkingInfoService parkingInfoService;

    /** LLM 提供商配置，可选值：mock / deepseek / openai */
    @Value("${llm.provider:mock}")
    private String llmProvider;

    /**
     * 构造函数
     * 
     * @param parkingInfoService 停车场信息服务
     */
    public RagService(ParkingInfoService parkingInfoService) {
        this.parkingInfoService = parkingInfoService;
    }

    /**
     * 生成回答
     * 
     * 根据配置的 LLM 提供商选择不同的回答生成方式。
     * 
     * @param parkingId 停车场 ID
     * @param question 用户问题
     * @return 生成的回答文本
     */
    public String generateAnswer(String parkingId, String question) {
        ParkingInfo parking = parkingInfoService.getById(parkingId);
        if (parking == null) {
            return "抱歉，未找到该停车场信息。";
        }

        if ("mock".equals(llmProvider)) {
            return generateRuleBasedAnswer(parking, question);
        } else {
            return generateLlmAnswer(parking, question);
        }
    }

    /**
     * 基于规则生成回答
     * 
     * 使用关键词匹配方式，根据用户问题中的关键词选择预设的回答模板。
     * 支持的问题类型包括：营业时间、收费标准、剩余车位、地址、电话、设施、周边地标等。
     * 
     * @param parking 停车场信息
     * @param question 用户问题
     * @return 生成的回答文本
     */
    private String generateRuleBasedAnswer(ParkingInfo parking, String question) {
        String q = question.toLowerCase();

        if (matchesPattern(q, "时间|开门|关门|营业|几点|早上|晚上|24小时")) {
            return String.format("%s的营业时间是%s。", parking.getName(), parking.getOpenTime());
        }

        if (matchesPattern(q, "收费|价格|多少钱|费用|停车费|怎么收费")) {
            return String.format("%s的收费标准是：%s", parking.getName(), parking.getFeeStandard());
        }

        if (matchesPattern(q, "车位|有没有车位|剩余车位|空车位|还有位置|还有车位|空位")) {
            int available = parkingInfoService.getAvailableSpaces(parking.getId());
            return String.format("%s目前还有%d个空余车位，总车位%d个。",
                    parking.getName(), available, parking.getTotalSpaces());
        }

        if (matchesPattern(q, "地址|在哪|位置|怎么走|导航|地点")) {
            return String.format("%s位于%s。", parking.getName(), parking.getAddress());
        }

        if (matchesPattern(q, "电话|联系方式|联系电话|号码")) {
            return String.format("%s的联系电话是%s。", parking.getName(), parking.getPhone());
        }

        if (matchesPattern(q, "设施|服务|有什么|充电桩|充电")) {
            String facilities = String.join("、", parking.getFacilities());
            return String.format("%s提供以下服务和设施：%s。", parking.getName(), facilities);
        }

        if (matchesPattern(q, "周边|附近|地标|旁边|周围")) {
            String landmarks = String.join("、", parking.getNearbyLandmarks());
            return String.format("%s周边有：%s。", parking.getName(), landmarks);
        }

        if (matchesPattern(q, "介绍|简介|怎么样|好不好|说说")) {
            return String.format("%s：%s 收费标准：%s",
                    parking.getName(), parking.getDescription(), parking.getFeeStandard());
        }

        return String.format("关于您问的\"%s\"，我查询到%s的信息如下：%s 营业时间%s，收费标准是%s。如需更多信息，您可以拨打停车场电话%s咨询。",
                question, parking.getName(), parking.getDescription(),
                parking.getOpenTime(), parking.getFeeStandard(), parking.getPhone());
    }

    /**
     * 判断文本是否匹配关键词模式
     * 
     * 使用竖线分隔的关键词列表进行匹配，如果文本包含任一关键词则返回 true。
     * 
     * @param text 待匹配的文本
     * @param pattern 关键词模式（用竖线分隔）
     * @return true 表示匹配成功，false 表示未匹配
     */
    private boolean matchesPattern(String text, String pattern) {
        String[] keywords = pattern.split("\\|");
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用 LLM 生成回答
     * 
     * 构建上下文和提示词，调用 LLM 生成自然语言回答。
     * 如果 LLM 调用失败，回退到规则模式。
     * 
     * @param parking 停车场信息
     * @param question 用户问题
     * @return 生成的回答文本
     */
    private String generateLlmAnswer(ParkingInfo parking, String question) {
        String context = buildContext(parking);
        String prompt = buildPrompt(parking.getName(), context, question);

        try {
            return callLlm(prompt);
        } catch (Exception e) {
            log.error("LLM 调用失败，回退到规则回答", e);
            return generateRuleBasedAnswer(parking, question);
        }
    }

    /**
     * 构建上下文信息
     * 
     * 将停车场信息格式化为文本，作为 LLM 的上下文输入。
     * 
     * @param parking 停车场信息
     * @return 格式化的上下文文本
     */
    private String buildContext(ParkingInfo parking) {
        StringBuilder sb = new StringBuilder();
        sb.append("停车场名称：").append(parking.getName()).append("\n");
        sb.append("停车场地址：").append(parking.getAddress()).append("\n");
        sb.append("联系电话：").append(parking.getPhone()).append("\n");
        sb.append("总车位：").append(parking.getTotalSpaces()).append("个\n");
        sb.append("当前剩余车位：").append(parkingInfoService.getAvailableSpaces(parking.getId())).append("个\n");
        sb.append("营业时间：").append(parking.getOpenTime()).append("\n");
        sb.append("收费标准：").append(parking.getFeeStandard()).append("\n");
        sb.append("停车场介绍：").append(parking.getDescription()).append("\n");
        sb.append("设施服务：").append(String.join("、", parking.getFacilities())).append("\n");
        sb.append("周边地标：").append(String.join("、", parking.getNearbyLandmarks())).append("\n");
        return sb.toString();
    }

    /**
     * 构建提示词
     * 
     * 将上下文和用户问题组合成完整的 LLM 提示词。
     * 
     * @param parkingName 停车场名称
     * @param context 上下文信息
     * @param question 用户问题
     * @return 完整的提示词
     */
    private String buildPrompt(String parkingName, String context, String question) {
        return "你是" + parkingName + "的智能客服，只回答与该停车场相关的问题。\n\n" +
                "已知信息：\n" + context + "\n\n" +
                "用户问题：" + question + "\n\n" +
                "请用简洁、自然的口语化中文回答。如果问题与停车场无关，礼貌地说明只回答停车相关问题。回答控制在2-3句话以内。";
    }

    /**
     * 调用 LLM 服务
     * 
     * 此方法为占位实现，实际项目中需要根据配置的提供商调用对应的 API。
     * 
     * @param prompt 提示词
     * @return LLM 返回的回答
     */
    private String callLlm(String prompt) {
        log.warn("LLM 调用未配置，请设置 llm.provider 为 deepseek 或 openai");
        return "抱歉，AI 服务暂时不可用，请稍后再试。";
    }
}
