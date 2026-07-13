package com.changping.parking.repository;

import com.changping.parking.model.CallRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话记录数据仓库
 * 
 * 提供通话记录数据的 CRUD 操作和统计分析方法。
 * 
 * @author Changping Parking AI Team
 */
@Repository
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    /**
     * 根据会话 ID 查询通话记录
     * 
     * @param sessionId 会话 ID
     * @return 通话记录
     */
    CallRecord findBySessionId(String sessionId);

    /**
     * 根据主叫号码查询通话记录
     * 
     * @param callerNumber 主叫号码
     * @return 通话记录列表
     */
    List<CallRecord> findByCallerNumber(String callerNumber);

    /**
     * 根据停车场 ID 查询通话记录
     * 
     * @param parkingId 停车场 ID
     * @return 通话记录列表
     */
    List<CallRecord> findByParkingId(String parkingId);

    /**
     * 根据时间范围查询通话记录
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 通话记录列表
     */
    List<CallRecord> findByStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定停车场的通话次数
     * 
     * @param parkingId 停车场 ID
     * @return 通话次数
     */
    long countByParkingId(String parkingId);

    /**
     * 统计指定时间范围内的通话次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 通话次数
     */
    long countByStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询热门停车场（按通话次数排序）
     * 
     * @param limit 返回数量
     * @return 停车场 ID 列表
     */
    @Query("SELECT cr.parkingId FROM CallRecord cr WHERE cr.parkingId IS NOT NULL GROUP BY cr.parkingId ORDER BY COUNT(cr) DESC")
    List<String> findHotParkingIds(Pageable pageable);

    /**
     * 统计每日通话量
     * 
     * @param date 日期
     * @return 通话次数
     */
    @Query("SELECT COUNT(cr) FROM CallRecord cr WHERE DATE(cr.startTime) = DATE(:date)")
    long countByDate(@Param("date") LocalDateTime date);
}