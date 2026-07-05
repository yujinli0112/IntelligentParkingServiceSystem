package com.changping.parking.service;

import com.changping.parking.model.CallRecord;
import com.changping.parking.model.CallSession;
import com.changping.parking.repository.CallRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话记录服务
 * 
 * 提供通话记录的保存和查询功能。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Service
public class CallRecordService {

    private final CallRecordRepository callRecordRepository;

    public CallRecordService(CallRecordRepository callRecordRepository) {
        this.callRecordRepository = callRecordRepository;
    }

    /**
     * 保存通话记录
     * 
     * 当会话结束时，将通话信息写入数据库。
     * 
     * @param session 通话会话
     */
    @Transactional
    public void saveRecord(CallSession session) {
        CallRecord record = new CallRecord();
        record.setSessionId(session.getSessionId());
        record.setCallerNumber(session.getCallerNumber());
        record.setCalledNumber(session.getCalledNumber());
        record.setParkingId(session.getCurrentParkingId());
        record.setParkingName(session.getCurrentParkingName());
        record.setDialogState(session.getState() != null ? session.getState().name() : null);
        record.setStartTime(session.getStartTime());
        record.setEndTime(LocalDateTime.now());
        record.setCallDuration(record.calculateDuration());

        callRecordRepository.save(record);
        log.info("通话记录已保存: sessionId={}, parking={}, duration={}s",
                record.getSessionId(),
                record.getParkingName(),
                record.getCallDuration());
    }

    /**
     * 根据会话 ID 查询通话记录
     * 
     * @param sessionId 会话 ID
     * @return 通话记录
     */
    public CallRecord findBySessionId(String sessionId) {
        return callRecordRepository.findBySessionId(sessionId);
    }

    /**
     * 根据主叫号码查询通话记录
     * 
     * @param callerNumber 主叫号码
     * @return 通话记录列表
     */
    public List<CallRecord> findByCallerNumber(String callerNumber) {
        return callRecordRepository.findByCallerNumber(callerNumber);
    }

    /**
     * 根据停车场查询通话记录
     * 
     * @param parkingId 停车场 ID
     * @return 通话记录列表
     */
    public List<CallRecord> findByParkingId(String parkingId) {
        return callRecordRepository.findByParkingId(parkingId);
    }

    /**
     * 获取热门停车场列表
     *
     * @param limit 返回数量
     * @return 停车场 ID 列表
     */
    public List<String> findHotParkingIds(int limit) {
        return callRecordRepository.findHotParkingIds(limit);
    }

    /**
     * 统计所有通话记录数量
     *
     * @return 通话记录数量
     */
    public long countAll() {
        return callRecordRepository.count();
    }

    /**
     * 查询所有通话记录
     *
     * @return 通话记录列表
     */
    public List<CallRecord> findAll() {
        return callRecordRepository.findAll();
    }

    /**
     * 统计指定停车场的通话次数
     * 
     * @param parkingId 停车场 ID
     * @return 通话次数
     */
    public long countByParkingId(String parkingId) {
        return callRecordRepository.countByParkingId(parkingId);
    }
}