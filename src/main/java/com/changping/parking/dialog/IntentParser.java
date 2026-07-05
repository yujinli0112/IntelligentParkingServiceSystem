package com.changping.parking.dialog;

import com.changping.parking.model.ParkingInfo;
import com.changping.parking.knowledge.ParkingInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

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
}
