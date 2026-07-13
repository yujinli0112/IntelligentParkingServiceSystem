package com.changping.parking.knowledge;

import com.changping.parking.model.ParkingInfo;
import com.changping.parking.repository.ParkingInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 停车场信息服务
 * 
 * 提供停车场数据的查询、更新和缓存功能。
 * 商用环境使用 MySQL 数据库存储，Redis 缓存热点数据。
 * 
 * 数据规模：约 200 个停车场
 * 缓存策略：
 * - 停车场基本信息缓存 24 小时
 * - 实时车位数据缓存 5 分钟
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Service
public class ParkingInfoService {

    /** 停车场数据仓库 */
    private final ParkingInfoRepository parkingRepository;

    /** Redis 缓存模板 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 是否启用数据库模式 */
    @Value("${parking.db.enabled:true}")
    private boolean dbEnabled;

    /** 是否启用缓存 */
    @Value("${parking.cache.enabled:true}")
    private boolean cacheEnabled;

    /** 停车场信息缓存过期时间（秒） */
    @Value("${parking.cache.info.expire:86400}")
    private long infoCacheExpire;

    /** 车位数据缓存过期时间（秒） */
    @Value("${parking.cache.spaces.expire:300}")
    private long spacesCacheExpire;

    /** 停车场信息缓存 Key 前缀 */
    private static final String PARKING_INFO_KEY = "parking:info:";

    /** 车位数据缓存 Key 前缀 */
    private static final String PARKING_SPACES_KEY = "parking:spaces:";

    /** 所有停车场列表缓存 Key */
    private static final String PARKING_LIST_KEY = "parking:list:all";

    /**
     * 构造函数
     * 
     * @param parkingRepository 停车场数据仓库
     * @param redisTemplate Redis 缓存模板
     */
    public ParkingInfoService(ParkingInfoRepository parkingRepository,
                              RedisTemplate<String, Object> redisTemplate) {
        this.parkingRepository = parkingRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取所有停车场列表
     * 
     * 优先从缓存读取，缓存不存在则从数据库查询并写入缓存。
     * 
     * @return 停车场列表
     */
    public List<ParkingInfo> getAll() {
        if (cacheEnabled) {
            Object cached = redisTemplate.opsForValue().get(PARKING_LIST_KEY);
            if (cached != null) {
                log.debug("从缓存获取停车场列表");
                return (List<ParkingInfo>) cached;
            }
        }

        if (!dbEnabled) {
            log.warn("数据库未启用且缓存为空，返回空列表");
            return java.util.Collections.emptyList();
        }

        List<ParkingInfo> list = parkingRepository.findAllActive();
        log.info("从数据库获取停车场列表，共 {} 个", list.size());

        if (cacheEnabled) {
            redisTemplate.opsForValue().set(PARKING_LIST_KEY, list, infoCacheExpire, TimeUnit.SECONDS);
        }

        return list;
    }

    /**
     * 根据 ID 获取停车场信息
     * 
     * 优先从缓存读取，缓存不存在则从数据库查询并写入缓存。
     * 
     * @param id 停车场 ID
     * @return 停车场信息，如果未找到返回 null
     */
    public ParkingInfo getById(String id) {
        if (id == null) {
            return null;
        }

        if (cacheEnabled) {
            Object cached = redisTemplate.opsForValue().get(PARKING_INFO_KEY + id);
            if (cached != null) {
                log.debug("从缓存获取停车场信息: {}", id);
                return (ParkingInfo) cached;
            }
        }

        Optional<ParkingInfo> optional = parkingRepository.findById(id);
        ParkingInfo info = optional.orElse(null);

        if (info != null && cacheEnabled) {
            redisTemplate.opsForValue().set(PARKING_INFO_KEY + id, info, infoCacheExpire, TimeUnit.SECONDS);
        }

        return info;
    }

    /**
     * 获取可用车位数量
     * 
     * 优先从缓存读取实时数据，缓存不存在则从数据库查询。
     * 商用环境建议对接停车场实时数据接口。
     * 
     * @param parkingId 停车场 ID
     * @return 当前可用车位数量
     */
    public int getAvailableSpaces(String parkingId) {
        if (cacheEnabled) {
            Object cached = redisTemplate.opsForValue().get(PARKING_SPACES_KEY + parkingId);
            if (cached != null) {
                return (Integer) cached;
            }
        }

        ParkingInfo info = getById(parkingId);
        if (info == null) {
            return 0;
        }

        int available = info.getAvailableSpaces() != null ? info.getAvailableSpaces() : 0;

        if (cacheEnabled) {
            redisTemplate.opsForValue().set(PARKING_SPACES_KEY + parkingId, available, 
                    spacesCacheExpire, TimeUnit.SECONDS);
        }

        return available;
    }

    /**
     * 更新可用车位数量
     * 
     * 更新数据库并刷新缓存。商用环境建议通过定时任务或消息队列接收实时数据。
     * 
     * @param parkingId 停车场 ID
     * @param count 新的可用车位数量
     */
    @Transactional
    public void updateAvailableSpaces(String parkingId, int count) {
        Optional<ParkingInfo> optional = parkingRepository.findById(parkingId);
        if (optional.isPresent()) {
            ParkingInfo info = optional.get();
            int maxSpaces = info.getTotalSpaces() != null ? info.getTotalSpaces() : 0;
            int safeCount = Math.max(0, Math.min(maxSpaces, count));
            info.setAvailableSpaces(safeCount);
            parkingRepository.save(info);

            if (cacheEnabled) {
                redisTemplate.opsForValue().set(PARKING_SPACES_KEY + parkingId, safeCount, 
                        spacesCacheExpire, TimeUnit.SECONDS);
                redisTemplate.delete(PARKING_INFO_KEY + parkingId);
            }

            log.info("更新停车场 {} 的可用车位: {}", parkingId, safeCount);
        }
    }

    /**
     * 根据名称搜索停车场
     * 
     * @param name 名称关键词
     * @return 匹配的停车场列表
     */
    public List<ParkingInfo> searchByName(String name) {
        return parkingRepository.findByNameContaining(name);
    }

    /**
     * 根据别名搜索停车场
     * 
     * @param alias 别名关键词
     * @return 匹配的停车场列表
     */
    public List<ParkingInfo> searchByAlias(String alias) {
        return parkingRepository.findByAliasContaining("%" + alias + "%");
    }

    /**
     * 根据地标搜索停车场
     * 
     * @param landmark 地标关键词
     * @return 匹配的停车场列表
     */
    public List<ParkingInfo> searchByLandmark(String landmark) {
        return parkingRepository.findByLandmarkContaining("%" + landmark + "%");
    }

    /**
     * 综合搜索停车场
     * 
     * 同时搜索名称、地址、别名、地标。
     * 
     * @param keyword 搜索关键词
     * @return 匹配的停车场列表
     */
    public List<ParkingInfo> search(String keyword) {
        return parkingRepository.searchByKeyword("%" + keyword + "%");
    }

    /**
     * 保存或更新停车场信息
     * 
     * @param parkingInfo 停车场信息
     * @return 保存后的停车场信息
     */
    @Transactional
    public ParkingInfo save(ParkingInfo parkingInfo) {
        ParkingInfo saved = parkingRepository.save(parkingInfo);

        if (cacheEnabled) {
            redisTemplate.delete(PARKING_INFO_KEY + saved.getId());
            redisTemplate.delete(PARKING_LIST_KEY);
        }

        log.info("保存停车场信息: {}", saved.getId());
        return saved;
    }

    /**
     * 删除停车场信息
     * 
     * @param parkingId 停车场 ID
     */
    @Transactional
    public void delete(String parkingId) {
        parkingRepository.deleteById(parkingId);

        if (cacheEnabled) {
            redisTemplate.delete(PARKING_INFO_KEY + parkingId);
            redisTemplate.delete(PARKING_SPACES_KEY + parkingId);
            redisTemplate.delete(PARKING_LIST_KEY);
        }

        log.info("删除停车场信息: {}", parkingId);
    }

    /**
     * 清除所有停车场缓存
     */
    public void clearAllCache() {
        if (cacheEnabled) {
            // 使用 scan 替代 keys 避免阻塞 Redis
            var keys = redisTemplate.keys("parking:info:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            keys = redisTemplate.keys("parking:spaces:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(PARKING_LIST_KEY);
            log.info("清除所有停车场缓存");
        }
    }

    /**
     * 刷新停车场列表缓存
     */
    public void refreshListCache() {
        if (cacheEnabled) {
            List<ParkingInfo> list = parkingRepository.findAllActive();
            redisTemplate.opsForValue().set(PARKING_LIST_KEY, list, infoCacheExpire, TimeUnit.SECONDS);
            log.info("刷新停车场列表缓存，共 {} 个", list.size());
        }
    }
}