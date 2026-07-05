package com.changping.parking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 昌平区智能停车电话客服系统主应用类
 * 
 * 本系统是一个基于 Spring Boot 的智能停车电话客服系统，集成了：
 * - FreeSWITCH 电话服务器（处理通话）
 * - ASR（语音识别）服务（mock / 阿里云）
 * - TTS（语音合成）服务（mock / 阿里云）
 * - RAG（检索增强生成）服务（规则匹配 / LLM）
 * - MySQL 数据库（停车场数据存储）
 * - Redis 缓存（分布式会话和数据缓存）
 * - Elasticsearch（可选，用于全文搜索）
 * 
 * 商用环境数据规模：约 200 个停车场
 * 
 * 启动方式：
 * 1. 开发环境：直接运行此类的 main 方法
 * 2. 生产环境：使用 Maven 打包后运行 jar 文件
 * 
 * @author Changping Parking AI Team
 */
@SpringBootApplication(exclude = {
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.changping.parking.repository")
@EnableTransactionManagement
@EnableAsync
public class ParkingAiApplication {

    /**
     * 应用入口
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ParkingAiApplication.class, args);
    }
}