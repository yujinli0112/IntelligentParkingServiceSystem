package com.changping.parking.knowledge;

import com.changping.parking.model.ParkingInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 停车场知识库加载器
 * 
 * 将停车场数据加载到 Elasticsearch 索引中，支持全文搜索和高亮显示。
 * 使用条件：配置 `knowledge.es.enabled=true`
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.es.enabled", havingValue = "true")
public class ParkingKnowledgeLoader {

    /** 停车场数据文件路径，默认从 classpath 加载 */
    @Value("${knowledge.dataFile:classpath:parking-data.json}")
    private Resource dataFile;

    /** Elasticsearch 仓库，用于数据存储和查询 */
    private final ParkingElasticsearchRepository esRepository;

    /** 停车场信息服务，用于获取内存中的数据 */
    private final ParkingInfoService parkingInfoService;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     * 
     * @param esRepository Elasticsearch 仓库
     * @param parkingInfoService 停车场信息服务
     */
    public ParkingKnowledgeLoader(ParkingElasticsearchRepository esRepository,
                                  ParkingInfoService parkingInfoService) {
        this.esRepository = esRepository;
        this.parkingInfoService = parkingInfoService;
    }

    /**
     * 初始化方法，将停车场数据加载到 Elasticsearch
     */
    @PostConstruct
    public void init() {
        try {
            log.info("开始初始化知识库...");

            List<ParkingInfo> parkingList = objectMapper.readValue(
                    dataFile.getInputStream(),
                    new TypeReference<List<ParkingInfo>>() {}
            );

            esRepository.saveAll(parkingList);
            log.info("ES 索引初始化完成，共 {} 条记录", parkingList.size());

        } catch (Exception e) {
            log.warn("ES 知识库初始化失败（可能 ES 未启动），将使用内存数据: {}", e.getMessage());
        }
    }
}
