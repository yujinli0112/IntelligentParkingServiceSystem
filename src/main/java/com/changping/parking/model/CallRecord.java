package com.changping.parking.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 通话记录实体类
 * 
 * 记录每次通话的详细信息，用于运营统计和数据分析。
 * 
 * @author Changping Parking AI Team
 */
@Data
@Entity
@Table(name = "call_records", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_caller_number", columnList = "caller_number"),
    @Index(name = "idx_parking_id", columnList = "parking_id"),
    @Index(name = "idx_start_time", columnList = "start_time")
})
public class CallRecord {

    /** 记录 ID（自增主键） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 会话 ID（唯一标识） */
    @Column(name = "session_id", length = 50, nullable = false)
    private String sessionId;

    /** 主叫号码（用户拨打的电话号码） */
    @Column(name = "caller_number", length = 20)
    private String callerNumber;

    /** 被叫号码（系统接入号码） */
    @Column(name = "called_number", length = 20)
    private String calledNumber;

    /** 咨询的停车场 ID */
    @Column(name = "parking_id", length = 20)
    private String parkingId;

    /** 咨询的停车场名称 */
    @Column(name = "parking_name", length = 100)
    private String parkingName;

    /** 对话结束时的状态 */
    @Column(name = "dialog_state", length = 20)
    private String dialogState;

    /** 通话时长（秒） */
    @Column(name = "call_duration")
    private Integer callDuration;

    /** 通话开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 通话结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 记录创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 在持久化前自动设置创建时间
     */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * 计算通话时长（秒）
     * 
     * @return 通话时长
     */
    public Integer calculateDuration() {
        if (startTime != null && endTime != null) {
            return (int) java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return 0;
    }
}