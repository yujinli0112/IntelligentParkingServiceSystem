-- =============================================
-- 昌平区智能停车电话客服系统 - 数据库初始化脚本
-- =============================================
-- 数据规模：约 200 个停车场
-- 创建时间：2024-01-01
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS parking_ai 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE parking_ai;

-- =============================================
-- 停车场信息主表
-- =============================================
CREATE TABLE IF NOT EXISTS parking_info (
    id VARCHAR(20) PRIMARY KEY COMMENT '停车场唯一标识（如 P001）',
    name VARCHAR(100) NOT NULL COMMENT '停车场名称',
    address VARCHAR(200) COMMENT '停车场详细地址',
    phone VARCHAR(20) COMMENT '联系电话',
    total_spaces INT DEFAULT 0 COMMENT '总车位数',
    available_spaces INT DEFAULT 0 COMMENT '当前可用车位数量',
    open_time VARCHAR(50) COMMENT '营业时间（如 06:00-23:00）',
    fee_standard VARCHAR(500) COMMENT '收费标准文本描述',
    fee_detail TEXT COMMENT '收费详情 JSON 数据',
    description VARCHAR(500) COMMENT '停车场描述信息',
    status INT DEFAULT 1 COMMENT '停车场状态（1-正常运营，0-暂停运营）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
    
    INDEX idx_name (name),
    INDEX idx_address (address),
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车场信息表';

-- =============================================
-- 停车场别名表（用于口语化匹配）
-- =============================================
CREATE TABLE IF NOT EXISTS parking_aliases (
    parking_id VARCHAR(20) NOT NULL COMMENT '停车场 ID',
    alias VARCHAR(50) NOT NULL COMMENT '停车场别名',
    
    PRIMARY KEY (parking_id, alias),
    INDEX idx_alias (alias),
    FOREIGN KEY (parking_id) REFERENCES parking_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车场别名表';

-- =============================================
-- 停车场设施服务表
-- =============================================
CREATE TABLE IF NOT EXISTS parking_facilities (
    parking_id VARCHAR(20) NOT NULL COMMENT '停车场 ID',
    facility VARCHAR(30) NOT NULL COMMENT '设施服务名称',
    
    PRIMARY KEY (parking_id, facility),
    INDEX idx_facility (facility),
    FOREIGN KEY (parking_id) REFERENCES parking_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车场设施服务表';

-- =============================================
-- 停车场周边地标表（用于地标匹配）
-- =============================================
CREATE TABLE IF NOT EXISTS parking_landmarks (
    parking_id VARCHAR(20) NOT NULL COMMENT '停车场 ID',
    landmark VARCHAR(50) NOT NULL COMMENT '周边地标名称',
    
    PRIMARY KEY (parking_id, landmark),
    INDEX idx_landmark (landmark),
    FOREIGN KEY (parking_id) REFERENCES parking_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车场周边地标表';

-- =============================================
-- 通话记录表（可选，用于数据分析）
-- =============================================
CREATE TABLE IF NOT EXISTS call_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录 ID',
    session_id VARCHAR(50) NOT NULL COMMENT '会话 ID',
    caller_number VARCHAR(20) COMMENT '主叫号码',
    called_number VARCHAR(20) COMMENT '被叫号码',
    parking_id VARCHAR(20) COMMENT '咨询的停车场 ID',
    parking_name VARCHAR(100) COMMENT '咨询的停车场名称',
    dialog_state VARCHAR(20) COMMENT '对话结束时的状态',
    call_duration INT COMMENT '通话时长（秒）',
    start_time DATETIME COMMENT '通话开始时间',
    end_time DATETIME COMMENT '通话结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    
    INDEX idx_session_id (session_id),
    INDEX idx_caller_number (caller_number),
    INDEX idx_parking_id (parking_id),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话记录表';

-- =============================================
-- 插入示例停车场数据（5 个示例）
-- =============================================
INSERT INTO parking_info (id, name, address, phone, total_spaces, available_spaces, open_time, fee_standard, description, status) VALUES
('P001', '西关环岛停车场', '西关环岛西北侧', '010-12345678', 200, 45, '全天24小时', '小型车首小时10元，后续每小时5元，单日最高60元', '位于西关环岛西北侧，交通便利', 1),
('P002', '体育馆停车场', '昌平区体育馆东侧', '010-23456789', 300, 120, '06:00-23:00', '小型车首小时8元，后续每小时4元，单日最高50元', '紧邻体育馆，适合观赛停车', 1),
('P003', '文化广场停车场', '昌平区文化广场地下', '010-34567890', 150, 30, '全天24小时', '小型车首小时12元，后续每小时6元，单日最高80元', '地下停车场，安全便捷', 1),
('P004', '世纪公园停车场', '世纪公园南门', '010-45678901', 400, 80, '06:00-22:00', '小型车首小时6元，后续每小时3元，单日最高40元', '公园配套停车场，环境优美', 1),
('P005', '政务中心停车场', '政务服务中心北侧', '010-56789012', 100, 50, '08:00-18:00', '免费停车2小时，超时每小时5元', '政务服务中心配套停车场', 1);

-- 插入别名数据
INSERT INTO parking_aliases (parking_id, alias) VALUES
('P001', '西关那家'),
('P001', '环岛停车场'),
('P002', '体育馆那家'),
('P002', '球场停车场'),
('P003', '广场停车场'),
('P003', '地下停车场'),
('P004', '公园停车场'),
('P005', '政务中心');

-- 插入设施服务数据
INSERT INTO parking_facilities (parking_id, facility) VALUES
('P001', '充电桩'),
('P001', '无感支付'),
('P001', '24小时监控'),
('P002', '充电桩'),
('P002', '洗车服务'),
('P003', '无感支付'),
('P003', '24小时监控'),
('P003', '代客泊车'),
('P004', '充电桩'),
('P004', '无感支付'),
('P005', '免费停车');

-- 插入周边地标数据
INSERT INTO parking_landmarks (parking_id, landmark) VALUES
('P001', '西关环岛'),
('P001', '商业街'),
('P001', '地铁站'),
('P002', '体育馆'),
('P002', '游泳馆'),
('P003', '文化广场'),
('P003', '图书馆'),
('P003', '博物馆'),
('P004', '世纪公园'),
('P004', '儿童乐园'),
('P005', '政务服务中心'),
('P005', '行政审批大厅');

-- =============================================
-- 创建视图：停车场综合信息视图
-- =============================================
CREATE OR REPLACE VIEW v_parking_full AS
SELECT 
    p.id,
    p.name,
    p.address,
    p.phone,
    p.total_spaces,
    p.available_spaces,
    p.open_time,
    p.fee_standard,
    p.description,
    p.status,
    GROUP_CONCAT(DISTINCT a.alias) AS aliases,
    GROUP_CONCAT(DISTINCT f.facility) AS facilities,
    GROUP_CONCAT(DISTINCT l.landmark) AS landmarks
FROM parking_info p
LEFT JOIN parking_aliases a ON p.id = a.parking_id
LEFT JOIN parking_facilities f ON p.id = f.parking_id
LEFT JOIN parking_landmarks l ON p.id = l.parking_id
GROUP BY p.id;

-- =============================================
-- 完成
-- =============================================
SELECT '数据库初始化完成！' AS message;
SELECT COUNT(*) AS parking_count FROM parking_info;