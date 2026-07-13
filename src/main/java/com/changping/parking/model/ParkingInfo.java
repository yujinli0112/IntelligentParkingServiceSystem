package com.changping.parking.model;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 停车场信息实体类
 * 
 * 存储停车场的基本信息，包括名称、地址、营业时间、收费标准等。
 * 支持 MySQL 数据库存储（JPA）和 Elasticsearch 全文搜索。
 * 
 * 商用环境数据量：约 200 个停车场
 * 
 * @author Changping Parking AI Team
 */
@Data
@Entity
@Table(name = "parking_info", indexes = {
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_address", columnList = "address"),
    @Index(name = "idx_phone", columnList = "phone")
})
@Document(indexName = "parking_info")
public class ParkingInfo {

    /** 停车场唯一标识，格式为 P+数字（如 P001）
     *  @javax.persistence.Id 用于 JPA，@org.springframework.data.annotation.Id 用于 Elasticsearch */
    @javax.persistence.Id
    @org.springframework.data.annotation.Id
    @Column(name = "id", length = 20)
    private String id;

    /** 停车场名称，支持中文分词搜索 */
    @Column(name = "name", length = 100, nullable = false)
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /** 停车场别名列表，用于模糊匹配用户口语化输入（如"西关那家"） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parking_aliases", joinColumns = @JoinColumn(name = "parking_id"))
    @Column(name = "alias", length = 50)
    @Field(type = FieldType.Keyword)
    private Set<String> aliases = new HashSet<>();

    /** 停车场详细地址 */
    @Column(name = "address", length = 200)
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String address;

    /** 联系电话 */
    @Column(name = "phone", length = 20)
    @Field(type = FieldType.Keyword)
    private String phone;

    /** 总车位数 */
    @Column(name = "total_spaces")
    @Field(type = FieldType.Integer)
    private Integer totalSpaces;

    /** 当前可用车位数量，支持实时更新 */
    @Column(name = "available_spaces")
    @Field(type = FieldType.Integer)
    private Integer availableSpaces;

    /** 营业时间描述（如"06:00-23:00"或"全天24小时"） */
    @Column(name = "open_time", length = 50)
    @Field(type = FieldType.Keyword)
    private String openTime;

    /** 收费标准文本描述 */
    @Column(name = "fee_standard", length = 500)
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String feeStandard;

    /** 收费详情 JSON 字符串（包含首小时、后续小时、单日最高等） */
    @Column(name = "fee_detail", columnDefinition = "TEXT")
    @Field(type = FieldType.Object)
    private String feeDetailJson;

    /** 停车场描述信息 */
    @Column(name = "description", length = 500)
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;

    /** 设施服务列表（如充电桩、无感支付等） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parking_facilities", joinColumns = @JoinColumn(name = "parking_id"))
    @Column(name = "facility", length = 30)
    @Field(type = FieldType.Keyword)
    private Set<String> facilities = new HashSet<>();

    /** 周边地标列表，用于地标匹配 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parking_landmarks", joinColumns = @JoinColumn(name = "parking_id"))
    @Column(name = "landmark", length = 50)
    @Field(type = FieldType.Keyword)
    private Set<String> nearbyLandmarks = new HashSet<>();

    /** 数据创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 数据更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 停车场状态（1-正常运营，0-暂停运营） */
    @Column(name = "status")
    private Integer status;

    /**
     * 在持久化前自动设置创建时间和更新时间
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = 1;
        }
    }

    /**
     * 在更新前自动设置更新时间
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}