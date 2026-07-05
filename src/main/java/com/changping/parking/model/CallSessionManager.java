package com.changping.parking.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 通话会话管理器
 * 
 * 管理所有通话会话，支持两种模式：
 * 1. 本地模式：使用 ConcurrentHashMap 存储会话（单实例部署）
 * 2. 分布式模式：使用 Redis 存储会话（多实例部署）
 * 
 * 商用环境建议使用分布式模式，以支持负载均衡和高可用。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Component
public class CallSessionManager {

    /** Redis 缓存模板 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 是否启用分布式会话存储 */
    @Value("${session.distributed.enabled:false}")
    private boolean distributedEnabled;

    /** 会话过期时间（秒） */
    @Value("${session.expire:3600}")
    private long sessionExpire;

    /** 本地会话存储（用于单实例模式） */
    private final ConcurrentHashMap<String, CallSession> localSessions = new ConcurrentHashMap<>();

    /** Redis 会话 Key 前缀 */
    private static final String SESSION_KEY = "call:session:";

    /**
     * 构造函数
     * 
     * @param redisTemplate Redis 缓存模板
     */
    public CallSessionManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 根据 sessionId 获取会话
     * 
     * 优先从本地缓存读取，如果启用分布式模式则从 Redis 读取。
     * 
     * @param sessionId 会话唯一标识
     * @return 会话对象，如果不存在返回 null
     */
    public CallSession getSession(String sessionId) {
        CallSession localSession = localSessions.get(sessionId);
        if (localSession != null) {
            return localSession;
        }

        if (distributedEnabled) {
            Object redisSession = redisTemplate.opsForValue().get(SESSION_KEY + sessionId);
            if (redisSession != null) {
                CallSession session = (CallSession) redisSession;
                localSessions.put(sessionId, session);
                return session;
            }
        }

        return null;
    }

    /**
     * 创建新会话
     * 
     * 同时保存到本地和 Redis（如果启用分布式模式）。
     * 
     * @param sessionId 会话唯一标识
     * @return 新创建的会话对象
     */
    public CallSession createSession(String sessionId) {
        CallSession session = new CallSession();
        session.setSessionId(sessionId);

        localSessions.put(sessionId, session);

        if (distributedEnabled) {
            redisTemplate.opsForValue().set(SESSION_KEY + sessionId, session, 
                    sessionExpire, TimeUnit.SECONDS);
            log.debug("创建分布式会话: {}", sessionId);
        }

        log.info("创建会话: {}", sessionId);
        return session;
    }

    /**
     * 删除会话
     * 
     * 同时从本地和 Redis 删除。
     * 
     * @param sessionId 会话唯一标识
     */
    public void removeSession(String sessionId) {
        localSessions.remove(sessionId);

        if (distributedEnabled) {
            redisTemplate.delete(SESSION_KEY + sessionId);
            log.debug("删除分布式会话: {}", sessionId);
        }

        log.info("删除会话: {}", sessionId);
    }

    /**
     * 更新会话状态
     * 
     * 同时更新本地和 Redis（如果启用分布式模式）。
     * 
     * @param session 会话对象
     */
    public void updateSession(CallSession session) {
        localSessions.put(session.getSessionId(), session);

        if (distributedEnabled) {
            redisTemplate.opsForValue().set(SESSION_KEY + session.getSessionId(), session, 
                    sessionExpire, TimeUnit.SECONDS);
            log.debug("更新分布式会话: {}", session.getSessionId());
        }
    }

    /**
     * 判断会话是否存在
     * 
     * @param sessionId 会话唯一标识
     * @return true 表示存在，false 表示不存在
     */
    public boolean hasSession(String sessionId) {
        if (localSessions.containsKey(sessionId)) {
            return true;
        }

        if (distributedEnabled) {
            return redisTemplate.hasKey(SESSION_KEY + sessionId);
        }

        return false;
    }

    /**
     * 获取当前活跃会话数量
     * 
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        if (distributedEnabled) {
            Set<String> keys = redisTemplate.keys(SESSION_KEY + "*");
            return keys != null ? keys.size() : 0;
        }
        return localSessions.size();
    }

    /**
     * 获取所有会话（仅本地模式有效）
     * 
     * @return 所有会话的集合
     */
    public Collection<CallSession> getAllSessions() {
        return localSessions.values();
    }

    /**
     * 清除所有过期会话
     */
    public void cleanExpiredSessions() {
        if (distributedEnabled) {
            log.info("Redis 会话自动过期，无需手动清理");
        } else {
            localSessions.entrySet().removeIf(entry -> {
                CallSession session = entry.getValue();
                if (session.getLastActivityTime() != null) {
                    long elapsed = java.time.Duration.between(
                            session.getLastActivityTime(), 
                            java.time.LocalDateTime.now()
                    ).getSeconds();
                    if (elapsed > sessionExpire) {
                        log.info("清理过期会话: {}", session.getSessionId());
                        return true;
                    }
                }
                return false;
            });
        }
    }
}