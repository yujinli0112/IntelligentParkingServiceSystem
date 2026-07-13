-- ============================================================
-- 停车场信息表商用扩展字段迁移
-- 执行日期: 2026-07-13
-- ============================================================

-- 1. 新增字段
ALTER TABLE `parking_info`
    ADD COLUMN `payment_methods` VARCHAR(200) DEFAULT NULL COMMENT '支付方式（逗号分隔：现金,微信,支付宝,ETC,银行卡）' AFTER `status`,
    ADD COLUMN `nearby_poi` TEXT DEFAULT NULL COMMENT '周边信息 JSON（美食、商场、医院、交通等）' AFTER `payment_methods`,
    ADD COLUMN `height_limit` DECIMAL(3,1) DEFAULT NULL COMMENT '限高（米），NULL 表示无限高' AFTER `nearby_poi`,
    ADD COLUMN `charging_stations` INT DEFAULT 0 COMMENT '充电桩数量' AFTER `height_limit`,
    ADD COLUMN `is_underground` TINYINT(1) DEFAULT 0 COMMENT '是否地下停车场（1-是，0-否）' AFTER `charging_stations`,
    ADD COLUMN `security` VARCHAR(100) DEFAULT NULL COMMENT '安保措施' AFTER `is_underground`,
    ADD COLUMN `holiday_fee` VARCHAR(200) DEFAULT NULL COMMENT '节假日收费标准' AFTER `security`,
    ADD COLUMN `monthly_rent` VARCHAR(200) DEFAULT NULL COMMENT '月租/包月信息' AFTER `holiday_fee`;

-- 2. 更新 P001 西关环岛停车场
UPDATE `parking_info` SET
    `payment_methods` = '现金,微信,支付宝,ETC,银行卡',
    `nearby_poi` = '{"美食":"西关饸饹面馆、老北京涮肉坊、张记烤鸭店","商场":"西关购物中心、华联超市","医院":"昌平区医院（步行5分钟）","交通":"西关环岛公交站（50米）、地铁昌平线昌平站（800米）","公园":"昌平公园（步行10分钟）"}',
    `height_limit` = 2.2,
    `charging_stations` = 4,
    `is_underground` = 0,
    `security` = '24小时监控、保安定时巡逻',
    `holiday_fee` = '节假日首小时12元，后续每小时6元，单日最高70元',
    `monthly_rent` = '小型车300元/月，大型车500元/月'
WHERE `id` = 'P001';

-- 3. 更新 P002 体育馆停车场
UPDATE `parking_info` SET
    `payment_methods` = '现金,微信,支付宝,ETC',
    `nearby_poi` = '{"美食":"体育馆美食街、麦当劳、肯德基","商场":"万达广场（步行8分钟）","医院":"昌平区中医院（1公里）","交通":"昌平体育馆公交站（100米）、地铁昌平线昌平东关站（600米）","健身":"昌平体育馆、游泳馆"}',
    `height_limit` = 2.5,
    `charging_stations` = 8,
    `is_underground` = 0,
    `security` = '24小时监控、无死角摄像头',
    `holiday_fee` = '节假日首小时10元，后续每小时5元，单日最高60元',
    `monthly_rent` = '小型车250元/月，大型车400元/月'
WHERE `id` = 'P002';

-- 4. 更新 P003 文化广场停车场
UPDATE `parking_info` SET
    `payment_methods` = '微信,支付宝,ETC',
    `nearby_poi` = '{"美食":"地下美食广场、日料店、星巴克","商场":"文化广场购物中心、新华书店","医院":"昌平区妇幼保健院（500米）","交通":"文化广场公交站（50米）","文化":"昌平图书馆、昌平博物馆"}',
    `height_limit` = 2.0,
    `charging_stations` = 6,
    `is_underground` = 1,
    `security` = '24小时监控、电子巡更系统',
    `holiday_fee` = '节假日首小时15元，后续每小时8元，单日最高90元',
    `monthly_rent` = '小型车400元/月'
WHERE `id` = 'P003';

-- 5. 更新 P004 世纪公园停车场
UPDATE `parking_info` SET
    `payment_methods` = '现金,微信,支付宝',
    `nearby_poi` = '{"美食":"公园烧烤、农家菜馆、茶社","商场":"世纪联华超市（步行5分钟）","医院":"昌平区医院（2公里）","交通":"世纪公园南门公交站（100米）","公园":"世纪公园（紧邻，可野餐、划船）"}',
    `height_limit` = NULL,
    `charging_stations` = 2,
    `is_underground` = 0,
    `security` = '白天保安值守、夜间巡逻',
    `holiday_fee` = '节假日首小时8元，后续每小时4元，单日最高50元',
    `monthly_rent` = '小型车200元/月'
WHERE `id` = 'P004';

-- 6. 更新 P005 政务中心停车场
UPDATE `parking_info` SET
    `payment_methods` = '现金,微信,支付宝',
    `nearby_poi` = '{"美食":"政务中心食堂（对外开放）、湘菜馆、粥铺","商场":"便民超市（步行3分钟）","医院":"昌平区医院（1.5公里）","交通":"政务中心公交站（100米）、地铁昌平线昌平站（1公里）","银行":"工商银行、建设银行（步行5分钟）"}',
    `height_limit` = 2.2,
    `charging_stations` = 0,
    `is_underground` = 0,
    `security` = '24小时监控、武警巡逻',
    `holiday_fee` = '同工作日标准',
    `monthly_rent` = '暂不支持月租'
WHERE `id` = 'P005';