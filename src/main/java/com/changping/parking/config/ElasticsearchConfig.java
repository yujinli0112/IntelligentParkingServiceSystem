package com.changping.parking.config;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置类
 * 
 * 配置 Elasticsearch 客户端和模板，支持全文搜索功能。
 * 使用条件：配置 `knowledge.es.enabled=true`
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "knowledge.es.enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackages = "com.changping.parking.knowledge")
public class ElasticsearchConfig {

    /** Elasticsearch 连接地址 */
    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String esUris;

    /**
     * 创建 Elasticsearch RestHighLevelClient
     * 
     * @return Elasticsearch 客户端实例
     */
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        log.info("初始化 Elasticsearch 客户端: {}", esUris);
        // 提取 host:port，去除协议前缀
        String hostPort = esUris.replaceFirst("^https?://", "");
        ClientConfiguration configuration = ClientConfiguration.builder()
                .connectedTo(hostPort)
                .withConnectTimeout(java.time.Duration.ofSeconds(5))
                .withSocketTimeout(java.time.Duration.ofSeconds(30))
                .build();
        return RestClients.create(configuration).rest();
    }

    /**
     * 创建 ElasticsearchRestTemplate
     * 
     * @return Elasticsearch 模板实例
     */
    @Bean
    public ElasticsearchRestTemplate elasticsearchTemplate() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}
