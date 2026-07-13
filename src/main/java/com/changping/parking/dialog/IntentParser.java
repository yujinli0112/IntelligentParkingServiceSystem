package com.changping.parking.dialog;

import com.changping.parking.model.ParkingInfo;
import com.changping.parking.knowledge.ParkingInfoService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 意图解析器
 * 
 * 用于从用户输入中识别停车场名称，支持四级匹配策略：
 * 1. 精确匹配 - 用户输入包含完整停车场名称
 * 2. 别名匹配 - 用户输入包含停车场别名（如"西关那家"）
 * 3. 模糊匹配 - 用户输入与停车场名称相似度大于等于50%
 * 4. 地标匹配 - 用户输入包含停车场周边地标或地址
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Component
public class IntentParser {

    /** 停车场信息服务，用于获取所有停车场数据 */
    private final ParkingInfoService parkingInfoService;

    /** LLM 模型，用于语义意图识别（可选注入，未配置时为 null） */
    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    /**
     * 构造函数
     * 
     * @param parkingInfoService 停车场信息服务
     */
    public IntentParser(ParkingInfoService parkingInfoService) {
        this.parkingInfoService = parkingInfoService;
    }

    /**
     * 匹配停车场
     * 
     * 使用四级匹配策略从用户输入中识别停车场，按优先级依次尝试：
     * 精确匹配 > 别名匹配 > 模糊匹配 > 地标匹配
     * 
     * @param userInput 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    public String matchParking(String userInput) {
        if (StringUtils.isBlank(userInput)) {
            return null;
        }

        String input = userInput.trim();

        String exactMatch = matchByExactName(input);
        if (exactMatch != null) {
            log.debug("精确匹配成功: {}", exactMatch);
            return exactMatch;
        }

        String aliasMatch = matchByAlias(input);
        if (aliasMatch != null) {
            log.debug("别名匹配成功: {}", aliasMatch);
            return aliasMatch;
        }

        String fuzzyMatch = matchByFuzzy(input);
        if (fuzzyMatch != null) {
            log.debug("模糊匹配成功: {}", fuzzyMatch);
            return fuzzyMatch;
        }

        String landmarkMatch = matchByLandmark(input);
        if (landmarkMatch != null) {
            log.debug("地标匹配成功: {}", landmarkMatch);
            return landmarkMatch;
        }

        log.warn("未匹配到停车场: {}", userInput);

        // 规则匹配失败，尝试 LLM 语义匹配
        if (chatLanguageModel != null) {
            String llmMatch = matchParkingByLlm(input);
            if (llmMatch != null) {
                log.info("LLM 语义匹配成功: {} -> {}", userInput, llmMatch);
                return llmMatch;
            }
        }

        return null;
    }

    /**
     * 根据停车场 ID 获取停车场名称
     * 
     * @param parkingId 停车场 ID
     * @return 停车场名称，如果未找到返回 null
     */
    public String getParkingName(String parkingId) {
        if (parkingId == null) {
            return null;
        }
        ParkingInfo info = parkingInfoService.getById(parkingId);
        return info != null ? info.getName() : null;
    }

    /**
     * 精确匹配
     * 
     * 判断用户输入是否包含完整的停车场名称。
     * 
     * @param input 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    private String matchByExactName(String input) {
        List<ParkingInfo> all = parkingInfoService.getAll();
        for (ParkingInfo p : all) {
            if (p.getName().equals(input) || input.contains(p.getName())) {
                return p.getId();
            }
        }
        return null;
    }

    /**
     * 别名匹配
     * 
     * 判断用户输入是否包含停车场的别名。
     * 
     * @param input 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    private String matchByAlias(String input) {
        List<ParkingInfo> all = parkingInfoService.getAll();
        for (ParkingInfo p : all) {
            if (p.getAliases() != null) {
                for (String alias : p.getAliases()) {
                    if (input.contains(alias) || alias.contains(input)) {
                        return p.getId();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 模糊匹配
     * 
     * 计算用户输入与停车场名称的相似度，相似度大于等于50%视为匹配成功。
     * 使用字符交集算法计算相似度。
     * 
     * @param input 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    private String matchByFuzzy(String input) {
        List<ParkingInfo> all = parkingInfoService.getAll();
        int bestScore = 0;
        String bestId = null;

        for (ParkingInfo p : all) {
            int score = calculateSimilarity(input, p.getName());
            if (p.getAliases() != null) {
                for (String alias : p.getAliases()) {
                    int aliasScore = calculateSimilarity(input, alias);
                    score = Math.max(score, aliasScore);
                }
            }
            if (score > bestScore && score >= 50) {
                bestScore = score;
                bestId = p.getId();
            }
        }

        return bestId;
    }

    /**
     * 地标匹配
     * 
     * 判断用户输入是否包含停车场的周边地标或地址。
     * 
     * @param input 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    private String matchByLandmark(String input) {
        List<ParkingInfo> all = parkingInfoService.getAll();
        for (ParkingInfo p : all) {
            if (p.getNearbyLandmarks() != null) {
                for (String landmark : p.getNearbyLandmarks()) {
                    if (input.contains(landmark)) {
                        return p.getId();
                    }
                }
            }
            if (p.getAddress() != null && input.contains(p.getAddress())) {
                return p.getId();
            }
        }
        return null;
    }

    /**
     * 计算字符串相似度
     * 
     * 使用字符交集算法，计算两个字符串的字符交集比例。
     * 
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度分数（0-100）
     */
    private int calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 100;

        int commonChars = 0;
        Set<Character> s2Chars = new HashSet<>();
        for (char c : s2.toCharArray()) {
            s2Chars.add(c);
        }
        for (char c : s1.toCharArray()) {
            if (s2Chars.contains(c)) {
                commonChars++;
            }
        }

        return (int) (100.0 * commonChars / Math.max(s1.length(), s2.length()));
    }

    /**
     * LLM 语义匹配停车场
     * 
     * 当规则匹配全部失败时，使用 LLM 进行语义理解。
     * 将用户输入和所有停车场信息发送给 LLM，由 LLM 判断用户指的是哪个停车场。
     * 如果 LLM 未配置或调用失败，返回 null。
     * 
     * @param userInput 用户输入文本
     * @return 匹配到的停车场 ID，如果未匹配到返回 null
     */
    private String matchParkingByLlm(String userInput) {
        try {
            List<ParkingInfo> all = parkingInfoService.getAll();
            if (all.isEmpty()) return null;

            // 构建停车场列表描述
            String parkingList = all.stream()
                    .map(p -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("ID=").append(p.getId())
                          .append(", 名称=").append(p.getName());
                        if (p.getAliases() != null && !p.getAliases().isEmpty()) {
                            sb.append(", 别名=").append(String.join("/", p.getAliases()));
                        }
                        if (p.getDescription() != null) {
                            sb.append(", 描述=").append(p.getDescription());
                        }
                        if (p.getAddress() != null) {
                            sb.append(", 地址=").append(p.getAddress());
                        }
                        return sb.toString();
                    })
                    .collect(Collectors.joining("\n"));

            String prompt = String.format(
                "你是一个停车场名称识别助手。用户会用口语化的方式描述停车场，你需要判断用户指的是下面哪个停车场。\n\n" +
                "停车场列表：\n%s\n\n" +
                "用户说：\"%s\"\n\n" +
                "请只回复停车场ID（如P001），如果都不匹配请回复NONE。不要回复其他内容。",
                parkingList, userInput
            );

            String response = chatLanguageModel.generate(prompt).trim();
            log.debug("LLM 意图识别响应: {}", response);

            // 验证返回的 ID 是否有效
            if ("NONE".equalsIgnoreCase(response)) return null;
            if (response.matches("P\\d+")) {
                ParkingInfo info = parkingInfoService.getById(response);
                if (info != null) return response;
            }
            return null;
        } catch (Exception e) {
            log.warn("LLM 语义匹配失败，回退到规则匹配: {}", e.getMessage());
            return null;
        }
    }
}
