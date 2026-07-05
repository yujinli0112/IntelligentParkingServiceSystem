package com.changping.parking.knowledge;

import com.changping.parking.model.ParkingInfo;
import org.springframework.data.elasticsearch.annotations.Highlight;
import org.springframework.data.elasticsearch.annotations.HighlightField;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 停车场 Elasticsearch 数据仓库
 * 
 * 提供停车场数据的 Elasticsearch 查询接口，支持高亮显示搜索结果。
 * 
 * @author Changping Parking AI Team
 */
@Repository
public interface ParkingElasticsearchRepository extends ElasticsearchRepository<ParkingInfo, String> {

    /**
     * 根据名称、别名或地址搜索停车场（带高亮）
     * 
     * 在 name、aliases、address 字段上进行搜索，并返回高亮结果。
     * 
     * @param name 名称关键词
     * @param aliases 别名关键词
     * @param address 地址关键词
     * @return 匹配的停车场列表（带高亮）
     */
    @Highlight(fields = {
            @HighlightField(name = "name"),
            @HighlightField(name = "aliases"),
            @HighlightField(name = "address")
    })
    List<ParkingInfo> findByNameOrAliasesOrAddressContaining(String name, String aliases, String address);

    /**
     * 根据名称搜索停车场
     * 
     * @param name 名称关键词
     * @return 匹配的停车场列表
     */
    List<ParkingInfo> findByNameContaining(String name);
}
