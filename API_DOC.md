# 昌平区智能停车电话客服系统 API 接口文档

## 目录

- [概述](#概述)
- [基础信息](#基础信息)
- [停车场管理接口](#停车场管理接口)
- [意图匹配接口](#意图匹配接口)
- [RAG问答接口](#rag问答接口)
- [对话管理接口](#对话管理接口)
- [会话状态接口](#会话状态接口)
- [通话记录接口](#通话记录接口)
- [错误码说明](#错误码说明)
- [数据库表结构](#数据库表结构)
- [附录](#附录)

---

## 概述

本系统是一个基于 Spring Boot 的智能停车电话客服系统，提供以下核心功能：

1. **停车场数据管理** - 查询停车场基本信息、车位状态等
2. **意图识别** - 解析用户输入，匹配停车场名称
3. **智能问答** - 基于RAG技术回答用户关于停车场的问题
4. **对话管理** - 支持完整的对话流程（识别→确认→问答）
5. **通话记录** - 记录每次通话用于数据分析

---

## 基础信息

| 项目 | 值 |
|------|-----|
| 基础URL | `http://localhost:8080/api` |
| 协议 | HTTP |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

---

## 停车场管理接口

### 1. 获取停车场列表

获取所有停车场信息列表。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/parking/list` |
| Method | GET |
| Content-Type | 无 |

**请求参数**

无

**响应示例**

```json
[
  {
    "id": "P001",
    "name": "西关环岛停车场",
    "address": "西关环岛西北侧",
    "phone": "010-12345678",
    "totalSpaces": 200,
    "availableSpaces": 45,
    "openTime": "全天24小时",
    "feeStandard": "小型车首小时10元，后续每小时5元，单日最高60元",
    "description": "位于西关环岛西北侧，交通便利",
    "aliases": ["西关那家", "环岛停车场"],
    "facilities": ["充电桩", "无感支付", "24小时监控"],
    "nearbyLandmarks": ["西关环岛", "商业街", "地铁站"],
    "status": 1,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  },
  {
    "id": "P002",
    "name": "体育馆停车场",
    "address": "昌平区体育馆东侧",
    "phone": "010-23456789",
    "totalSpaces": 300,
    "availableSpaces": 120,
    "openTime": "06:00-23:00",
    "feeStandard": "小型车首小时8元，后续每小时4元，单日最高50元",
    "description": "紧邻体育馆，适合观赛停车",
    "aliases": ["体育馆那家", "球场停车场"],
    "facilities": ["充电桩", "洗车服务"],
    "nearbyLandmarks": ["体育馆", "游泳馆"],
    "status": 1,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  }
]
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 停车场唯一标识（如P001） |
| name | String | 停车场名称 |
| address | String | 详细地址 |
| phone | String | 联系电话 |
| totalSpaces | Integer | 总车位数 |
| availableSpaces | Integer | 当前可用车位 |
| openTime | String | 营业时间 |
| feeStandard | String | 收费标准描述 |
| description | String | 停车场描述 |
| aliases | List<String> | 别名列表（用于口语匹配） |
| facilities | List<String> | 设施服务列表 |
| nearbyLandmarks | List<String> | 周边地标列表 |
| status | Integer | 状态（1-正常，0-暂停） |

---

### 2. 获取单个停车场详情

根据停车场ID获取详细信息。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/parking/{id}` |
| Method | GET |
| Content-Type | 无 |

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | 是 | 停车场ID |

**请求示例**

```
GET /api/parking/P001
```

**响应示例**

```json
{
  "id": "P001",
  "name": "西关环岛停车场",
  "address": "西关环岛西北侧",
  "phone": "010-12345678",
  "totalSpaces": 200,
  "availableSpaces": 45,
  "openTime": "全天24小时",
  "feeStandard": "小型车首小时10元，后续每小时5元，单日最高60元"
}
```

---

## 意图匹配接口

### 3. 测试意图匹配

输入文本，测试系统是否能正确识别停车场名称。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/intent/match` |
| Method | GET |
| Content-Type | 无 |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 用户输入文本 |

**请求示例**

```
GET /api/intent/match?text=西关环岛停车场
```

**响应示例**

```json
{
  "input": "西关环岛停车场",
  "parkingId": "P001",
  "parkingName": "西关环岛停车场"
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| input | String | 用户输入文本 |
| parkingId | String | 匹配到的停车场ID（未匹配时为null） |
| parkingName | String | 停车场名称（未匹配时为null） |

**匹配规则说明**

系统采用四级匹配策略：

1. **精确匹配** - 直接匹配停车场名称
2. **别名匹配** - 匹配预设的别名（如"西关那家"）
3. **模糊匹配** - 名称包含关键词
4. **地标匹配** - 匹配周边地标

---

## RAG问答接口

### 4. 基于RAG的智能问答

根据停车场ID和用户问题，生成智能回答。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/rag/ask` |
| Method | GET |
| Content-Type | 无 |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| parkingId | String | 是 | 停车场ID |
| question | String | 是 | 用户问题 |

**请求示例**

```
GET /api/rag/ask?parkingId=P001&question=几点关门
```

**响应示例**

```json
{
  "parkingId": "P001",
  "question": "几点关门",
  "answer": "西关环岛停车场的营业时间是全天24小时。"
}
```

**支持的问题类型**

- 营业时间（几点开门/关门）
- 收费标准（怎么收费）
- 车位情况（还有车位吗）
- 设施服务（有充电桩吗）
- 联系方式（电话多少）

---

## 对话管理接口

### 5. 开始对话

创建新的对话会话，返回会话ID和欢迎语。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/dialog/start` |
| Method | POST |
| Content-Type | application/json |

**请求参数**

无（无需请求体）

**响应示例**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "welcome": "您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？"
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话唯一标识（后续对话需携带） |
| welcome | String | 系统欢迎语 |

---

### 6. 发送消息

在已有会话中发送用户消息，获取系统回复。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/dialog/send` |
| Method | POST |
| Content-Type | application/json |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID（从start接口获取） |
| text | String | 是 | 用户消息内容 |

**请求示例**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "西关环岛停车场"
}
```

**响应示例**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "PARK_CONFIRMATION",
  "answer": "您想咨询的是西关环岛停车场对吗？",
  "currentParkingId": null,
  "currentParkingName": null
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID |
| state | String | 对话状态（见状态说明） |
| answer | String | 系统回复内容 |
| currentParkingId | String | 当前选定的停车场ID |
| currentParkingName | String | 当前选定的停车场名称 |

**对话状态说明**

| 状态 | 说明 |
|------|------|
| IDENTIFYING_PARK | 停车场识别阶段 |
| PARK_CONFIRMATION | 停车场确认阶段 |
| QA_LOOP | 问答阶段 |
| END | 对话结束 |

---

## 完整对话流程示例

### 流程图

```
开始对话 → 输入停车场名称 → 确认 → 问答 → 结束 → 保存通话记录
```

### Postman测试步骤

**步骤1：开始对话**

```
POST http://localhost:8080/api/dialog/start
Content-Type: application/json

(无需请求体)
```

**响应：**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "welcome": "您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？"
}
```

**步骤2：输入停车场名称**

```
POST http://localhost:8080/api/dialog/send
Content-Type: application/json

{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "西关环岛停车场"
}
```

**响应：**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "PARK_CONFIRMATION",
  "answer": "您想咨询的是西关环岛停车场对吗？"
}
```

**步骤3：确认停车场**

```
POST http://localhost:8080/api/dialog/send
Content-Type: application/json

{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "是的"
}
```

**响应：**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "QA_LOOP",
  "answer": "好的，已为您接入西关环岛停车场的智能咨询服务。您可以问我关于营业时间、收费标准、剩余车位等问题。",
  "currentParkingId": "P001",
  "currentParkingName": "西关环岛停车场"
}
```

**步骤4：提问**

```
POST http://localhost:8080/api/dialog/send
Content-Type: application/json

{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "怎么收费"
}
```

**响应：**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "QA_LOOP",
  "answer": "西关环岛停车场的收费标准是：小型车首小时10元，后续每小时5元，单日最高60元。"
}
```

**步骤5：结束测试会话并保存通话记录**

```
POST http://localhost:8080/api/dialog/end-test?sessionId=550e8400-e29b-41d4-a716-446655440000
```

**响应：**
```json
{
  "success": true,
  "message": "通话记录已保存",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 会话状态接口

### 7. 获取所有会话状态

查看当前系统中的活跃会话。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/sessions` |
| Method | GET |
| Content-Type | 无 |

**请求参数**

无

**响应示例**

```json
{
  "count": 2,
  "sessions": [
    {
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "state": "QA_LOOP",
      "currentParkingId": "P001",
      "currentParkingName": "西关环岛停车场"
    }
  ]
}
```

---

## 通话记录接口

### 8. 查询通话记录列表

查询系统中所有保存的通话记录。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/call-records` |
| Method | GET |
| Content-Type | 无 |

**请求参数**

无

**响应示例**

```json
{
  "count": 2,
  "records": [
    {
      "id": 1,
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "callerNumber": null,
      "calledNumber": null,
      "parkingId": "P001",
      "parkingName": "西关环岛停车场",
      "dialogState": "END",
      "callDuration": 45,
      "startTime": "2024-01-15T10:30:00",
      "endTime": "2024-01-15T10:30:45",
      "createdAt": "2024-01-15T10:30:45"
    },
    {
      "id": 2,
      "sessionId": "660e9511-f392-52e5-b827-557766551111",
      "callerNumber": null,
      "calledNumber": null,
      "parkingId": "P002",
      "parkingName": "体育馆停车场",
      "dialogState": "END",
      "callDuration": 120,
      "startTime": "2024-01-15T11:00:00",
      "endTime": "2024-01-15T11:02:00",
      "createdAt": "2024-01-15T11:02:00"
    }
  ]
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录ID（自增主键） |
| sessionId | String | 会话唯一标识 |
| callerNumber | String | 主叫号码（用户拨打的电话号码） |
| calledNumber | String | 被叫号码（系统接入号码） |
| parkingId | String | 咨询的停车场ID |
| parkingName | String | 咨询的停车场名称 |
| dialogState | String | 对话结束时的状态 |
| callDuration | Integer | 通话时长（秒） |
| startTime | LocalDateTime | 通话开始时间 |
| endTime | LocalDateTime | 通话结束时间 |
| createdAt | LocalDateTime | 记录创建时间 |

**对话状态说明**

| 状态 | 说明 |
|------|------|
| IDENTIFYING_PARK | 停车场识别阶段 |
| PARK_CONFIRMATION | 停车场确认阶段 |
| QA_LOOP | 问答阶段 |
| END | 正常结束 |

---

### 9. 结束会话并保存通话记录（测试用）

用于测试通话记录功能，强制结束指定会话并保存通话记录。

**请求信息**

| 项目 | 值 |
|------|-----|
| URL | `/dialog/end-test` |
| Method | POST |
| Content-Type | 无 |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 要结束的会话ID |

**请求示例**

```
POST http://localhost:8080/api/dialog/end-test?sessionId=550e8400-e29b-41d4-a716-446655440000
```

**响应示例**

```json
{
  "success": true,
  "message": "通话记录已保存",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**响应字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 是否成功 |
| message | String | 结果消息 |
| sessionId | String | 会话的ID |

---

## 错误码说明

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| 会话不存在 | sessionId无效或已过期 | 调用/dialog/start创建新会话 |
| 停车场不存在 | parkingId无效 | 检查停车场ID是否正确 |
| 400 Bad Request | 请求参数格式错误 | 检查Content-Type和请求体格式 |

---

## 数据库表结构

系统支持MySQL数据库存储，主要表结构如下：

### parking_info（停车场信息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(20) | 停车场ID（主键） |
| name | VARCHAR(100) | 停车场名称 |
| address | VARCHAR(200) | 地址 |
| phone | VARCHAR(20) | 电话 |
| total_spaces | INT | 总车位 |
| available_spaces | INT | 可用车位 |
| open_time | VARCHAR(50) | 营业时间 |
| fee_standard | VARCHAR(500) | 收费标准 |
| status | INT | 状态 |

### parking_aliases（别名表）

| 字段 | 类型 | 说明 |
|------|------|------|
| parking_id | VARCHAR(20) | 停车场ID（外键） |
| alias | VARCHAR(50) | 别名 |

### parking_landmarks（地标表）

| 字段 | 类型 | 说明 |
|------|------|------|
| parking_id | VARCHAR(20) | 停车场ID（外键） |
| landmark | VARCHAR(50) | 地标 |

### call_records（通话记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 记录ID（自增主键） |
| session_id | VARCHAR(50) | 会话ID（唯一标识） |
| caller_number | VARCHAR(20) | 主叫号码 |
| called_number | VARCHAR(20) | 被叫号码 |
| parking_id | VARCHAR(20) | 咨询的停车场ID |
| parking_name | VARCHAR(100) | 咨询的停车场名称 |
| dialog_state | VARCHAR(20) | 对话结束状态 |
| call_duration | INT | 通话时长（秒） |
| start_time | DATETIME | 通话开始时间 |
| end_time | DATETIME | 通话结束时间 |
| created_at | DATETIME | 记录创建时间 |

---

## 附录：停车场ID列表

| ID | 名称 | 别名 |
|----|------|------|
| P001 | 西关环岛停车场 | 西关那家、环岛停车场 |
| P002 | 体育馆停车场 | 体育馆那家、球场停车场 |
| P003 | 文化广场停车场 | 广场停车场、地下停车场 |
| P004 | 世纪公园停车场 | 公园停车场 |
| P005 | 政务中心停车场 | 政务中心 |

---

## 更新日志

| 版本 | 日期 | 更新内容 |
|------|------|---------|
| v1.1.0 | 2024-01-15 | 新增通话记录接口（/call-records, /dialog/end-test） |
| v1.0.0 | 2024-01-01 | 初始版本 |

---

*文档版本: v1.1.0*
*最后更新: 2024-01-15*