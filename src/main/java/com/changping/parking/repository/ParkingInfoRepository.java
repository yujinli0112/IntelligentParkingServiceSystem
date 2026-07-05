package com.changping.parking.repository;

import com.changping.parking.model.ParkingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 停车场信息数据仓库
 * 
 * 提供停车场数据的 CRUD 操作和自定义查询方法。
 * 使用 Spring Data JPA 自动实现数据库访问。
 * 
 * @author Changping Parking AI Team
 */
@Repository
public interface ParkingInfoRepository extends JpaRepository<ParkingInfo, String> {

    /**
     * 根据名称查询停车场
     * 
     * @param name 停车场名称
     * @return 停车场信息
     */
    Optional<ParkingInfo> findByName(String name);

    /**
     * 根据名称模糊查询停车场
     * 
     * @param name 名称关键词
     * @return 匹配的停车场列表
     */
    List<ParkingInfo> findByNameContaining(String name);

    /**
     * 根据地址模糊查询停车场
     * 
     * @param address 地址关键词
     * @return 匹配的停车场列表
     */
    List<ParkingInfo> findByAddressContaining(String address);

    /**
     * 根据状态查询停车场列表
     * 
     * @param status 停车场状态（1-正常运营）
     * @return 正常运营的停车场列表
     */
    List<ParkingInfo> findByStatus(Integer status);

    /**
     * 查询所有正常运营的停车场
     * 
     * @return 正常运营的停车场列表
     */
    @Query("SELECT p FROM ParkingInfo p WHERE p.status = 1")
    List<ParkingInfo> findAllActive();

    /**
     * 根据别名查询停车场
     * 
     * 使用 JOIN 查询别名表，匹配用户口语化输入。
     * 
     * @param alias 别名关键词
     * @return 匹配的停车场列表
     */
    @Query("SELECT p FROM ParkingInfo p JOIN p.aliases a WHERE a = :alias")
    List<ParkingInfo> findByAlias(@Param("alias") String alias);

    /**
     * 根据别名模糊查询停车场
     * 
     * @param alias 别名关键词
     * @return 匹配的停车场列表
     */
    @Query("SELECT p FROM ParkingInfo p JOIN p.aliases a WHERE a LIKE :alias")
    List<ParkingInfo> findByAliasContaining(@Param("alias") String alias);

    /**
     * 根据地标查询停车场
     * 
     * 使用 JOIN 查询地标表，匹配用户输入的周边地标。
     * 
     * @param landmark 地标关键词
     * @return 匹配的停车场列表
     */
    @Query("SELECT p FROM ParkingInfo p JOIN p.nearbyLandmarks l WHERE l = :landmark")
    List<ParkingInfo> findByLandmark(@Param("landmark") String landmark);

    /**
     * 根据地标模糊查询停车场
     * 
     * @param landmark 地标关键词
     * @return 匹配的停车场列表
     */
    @Query("SELECT p FROM ParkingInfo p JOIN p.nearbyLandmarks l WHERE l LIKE :landmark")
    List<ParkingInfo> findByLandmarkContaining(@Param("landmark") String landmark);

    /**
     * 综合搜索停车场（名称、地址、别名、地标）
     * 
     * @param keyword 搜索关键词
     * @return 匹配的停车场列表
     */
    @Query("SELECT DISTINCT p FROM ParkingInfo p " +
           "LEFT JOIN p.aliases a " +
           "LEFT JOIN p.nearbyLandmarks l " +
           "WHERE p.name LIKE :keyword " +
           "OR p.address LIKE :keyword " +
           "OR a LIKE :keyword " +
           "OR l LIKE :keyword")
    List<ParkingInfo> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据电话查询停车场
     * 
     * @param phone 电话号码
     * @return 停车场信息
     */
    Optional<ParkingInfo> findByPhone(String phone);

    /**
     * 统计正常运营的停车场数量
     * 
     * @return 停车场数量
     */
    @Query("SELECT COUNT(p) FROM ParkingInfo p WHERE p.status = 1")
    long countActive();
}