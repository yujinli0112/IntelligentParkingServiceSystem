# 昌平区智能停车电话客服系统

基于 FreeSWITCH + Spring Boot + AI 大模型的智能停车电话客服系统。实现用户通过手机 SIP 软电话拨打系统，自动识别停车场名称，回答营业时间、收费标准、剩余车位等问题。

---

## 目录

1. [系统架构](#1-系统架构)
   - [架构图](#11-架构图)
   - [核心流程](#12-核心流程)
2. [技术栈](#2-技术栈)
3. [快速开始](#3-快速开始)
   - [环境要求](#31-环境要求)
   - [启动基础设施](#32-启动基础设施)
   - [启动应用](#33-启动应用)
   - [配置 SIP 软电话](#34-配置-sip-软电话)
4. [核心模块详解](#4-核心模块详解)
   - [4.1 TCP 层 - 音频流处理](#41-tcp-层---音频流处理)
   - [4.2 语音服务层 - ASR/TTS](#42-语音服务层---asrtts)
   - [4.3 对话层 - 状态机与 RAG](#43-对话层---状态机与-rag)
   - [4.4 知识库层 - 数据管理](#44-知识库层---数据管理)
5. [配置说明](#5-配置说明)
   - [5.1 application.yml 完整配置](#51-applicationyml-完整配置)
   - [5.2 切换到真实云服务](#52-切换到真实云服务)
   - [5.3 启用 Elasticsearch](#53-启用-elasticsearch)
6. [API 接口文档](#6-api-接口文档)
   - [6.1 停车场管理](#61-停车场管理)
   - [6.2 意图匹配](#62-意图匹配)
   - [6.3 RAG 问答](#63-rag-问答)
   - [6.4 对话流程](#64-对话流程)
   - [6.5 会话管理](#65-会话管理)
7. [项目结构](#7-项目结构)
8. [数据模型](#8-数据模型)
   - [8.1 ParkingInfo](#81-parkinginfo)
   - [8.2 CallSession](#82-callsession)
   - [8.3 DialogState](#83-dialogstate)
9. [FreeSWITCH 配置](#9-freeswitch-配置)
   - [9.1 分机配置](#91-分机配置)
   - [9.2 拨号计划](#92-拨号计划)
10. [部署方案](#10-部署方案)
11. [可行性分析](#11-可行性分析)
12. [开发建议](#12-开发建议)
13. [扩展方向](#13-扩展方向)

---

## 1. 系统架构

### 1.1 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户手机端                                      │
│                        Zoiper / Linphone SIP 软电话                           │
│                                    │                                        │
│                              SIP 信令 (UDP 5060)                            │
│                                    ▼                                        │
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FreeSWITCH 软交换服务器 (Docker)                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────────────────┐    │
│  │   SIP 注册服务   │  │   ESL 管理接口   │  │  Outbound Socket APP      │    │
│  │   (端口 5060)   │  │   (端口 8021)   │  │   (端口 8084 - 音频流)    │    │
│  └────────┬────────┘  └────────┬────────┘  └────────────┬──────────────┘    │
│           │                    │                        │                    │
│           │ 注册/呼叫           │ 事件订阅/命令           │ PCM 音频双向流      │
│           ▼                    ▼                        ▼                    │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │                     Spring Boot 应用 (Java 17)                      │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                              TCP 8084 (音频流)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 后端服务 (详细架构)                          │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AudioSocketServer (Netty)                        │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │ 1. 监听 8084 端口                                             │  │   │
│  │  │ 2. 处理 FreeSWITCH socket 协议握手                            │  │   │
│  │  │ 3. 解析 CHANNEL_DATA 提取 PCM 音频                             │  │   │
│  │  │ 4. 发送 playback/hangup 命令到 FreeSWITCH                      │  │   │
│  │  └──────────────────────────┬───────────────────────────────────┘  │   │
│  └─────────────────────────────┼───────────────────────────────────────┘   │
│                                │                                            │
│          ┌─────────────────────┼─────────────────────┐                      │
│          ▼                     ▼                     ▼                      │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐          │
│  │ AsrService   │    │   DialogManager   │    │    TtsService    │          │
│  │ 语音识别服务  │    │   对话状态机      │    │   语音合成服务    │          │
│  │ ──────────   │    │ ──────────────   │    │ ──────────────   │          │
│  │ • 实时流式识别│    │ • 状态流转管理    │    │ • 文本转 WAV     │          │
│  │ • 长静音断句  │    │ • 意图解析调度    │    │ • 阿里云 API    │          │
│  │ • 阿里云 WS   │    │ • 上下文维护      │    │ • Mock 模式     │          │
│  │ • Mock 模式   │    │ • 确认机制        │    │                  │          │
│  └───────┬──────┘    └────────┬─────────┘    └────────┬─────────┘          │
│          │                    │                     │                      │
│          │ 识别文本            │ 停车场ID/问题        │ 合成文本             │
│          ▼                    ▼                     ▼                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        DialogManager (核心调度)                       │   │
│  │  ┌──────────┐    ┌──────────────┐    ┌──────────────┐              │   │
│  │  │WAITING   │───▶│IDENTIFYING   │───▶│CONFIRMATION  │              │   │
│  │  │WELCOME   │    │PARK          │    │PARK         │              │   │
│  │  └──────────┘    └──────┬───────┘    └──────┬───────┘              │   │
│  │                         │                   │                      │   │
│  │                         │ 未识别            │ 是                    │   │
│  │                         ▼                   ▼                      │   │
│  │                    ┌──────────┐        ┌──────────┐                 │   │
│  │                    │ 重试    │◀───────│  QA_LOOP │                 │   │
│  │                    └──────────┘        │  (问答)  │                 │   │
│  │                                        └────┬─────┘                 │   │
│  │                                             │ 再见/感谢              │   │
│  │                                             ▼                      │   │
│  │                                        ┌──────────┐                 │   │
│  │                                        │   END    │                 │   │
│  │                                        └──────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                │                                            │
│          ┌─────────────────────┼─────────────────────┐                      │
│          ▼                     ▼                     ▼                      │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐          │
│  │IntentParser  │    │    RagService     │    │  ParkingInfo     │          │
│  │停车场名匹配   │    │   检索增强生成     │    │    数据服务      │          │
│  │ ──────────   │    │ ──────────────   │    │ ──────────────   │          │
│  │ • 精确匹配    │    │ • 规则引擎        │    │ • JSON 数据加载  │          │
│  │ • 别名匹配    │    │ • LLM 调用        │    │ • ES 索引        │          │
│  │ • 模糊匹配    │    │ • Function        │    │ • 实时车位模拟   │          │
│  │ • 地标匹配    │    │   Calling         │    │                  │          │
│  └──────────────┘    └──────────────────┘    └──────────────────┘          │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      外部依赖 (可选)                                 │   │
│  │  ┌─────────────────┐    ┌─────────────────┐                        │   │
│  │  │ Elasticsearch   │    │   DeepSeek API   │                        │   │
│  │  │ (全文检索)      │    │   (LLM)         │                        │   │
│  │  └─────────────────┘    └─────────────────┘                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心流程

```
1. 用户拨打 123456
       │
       ▼
2. FreeSWITCH 接收呼叫，执行 socket APP
       │
       ▼
3. 建立 TCP 连接 (8084端口)，握手协议
       │
       ▼
4. 播放欢迎语："您好，欢迎致电..."
       │
       ▼
5. 用户说出停车场名称 (如"西关那家")
       │
       ▼
6. ASR 实时识别 → DialogManager
       │
       ▼
7. IntentParser 模糊匹配停车场
       │
       ▼
8. 系统确认："您想咨询的是XX停车场对吗？"
       │
       ▼
9. 用户确认 "是"/"不是"
       │
       ├── 不是 → 回到步骤5
       │
       ▼
10. 用户提问："几点关门？"/"怎么收费？"/"还有车位吗？"
       │
       ▼
11. RagService 生成回答 (规则引擎或 LLM)
       │
       ▼
12. TTS 合成音频 → FreeSWITCH 播放
       │
       ▼
13. 循环步骤10-12，直到用户说"再见"或挂断
       │
       ▼
14. 播放结束语，自动挂断
```

---

## 2. 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 应用框架 | Spring Boot | 2.7.18 | 主应用框架 |
| 语言 | Java | 17 | LTS 版本，性能稳定 |
| 网络通信 | Netty | 4.1.100.Final | 高性能 TCP/UDP 通信框架 |
| 电话交换 | FreeSWITCH | latest (Docker) | 开源软交换平台 |
| 语音识别 | 阿里云实时语音识别 | - | WebSocket 流式识别 |
| 语音合成 | 阿里云语音合成 | - | REST API |
| 大模型 | DeepSeek / OpenAI | - | 兼容 OpenAI 接口 |
| RAG 框架 | LangChain4j | 0.29.1 | 检索增强生成 |
| 全文检索 | Elasticsearch | 7.17.15 | 停车场信息索引 (可选) |
| 构建工具 | Maven | 3.6+ | 项目依赖管理 |
| 容器化 | Docker / Docker Compose | - | 基础设施部署 |

---

## 3. 快速开始

### 3.1 环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **Docker**: 支持 Docker Compose
- **网络**: 手机和电脑连接同一局域网

### 3.2 启动基础设施

```bash
# 进入项目目录
cd parking-ai-callcenter

# 启动 FreeSWITCH 和 Elasticsearch
docker-compose up -d

# 查看服务状态
docker-compose ps
```

### 3.3 启动应用

```bash
# 方法一：Maven 直接运行 (开发模式)
mvn spring-boot:run

# 方法二：打包后运行 (生产模式)
mvn clean package -DskipTests
java -jar target/parking-ai-callcenter-1.0.0.jar
```

**启动成功标志**:
```
2026-06-28 22:58:06.808  INFO 38492 --- [           main] c.c.parking.ParkingAiApplication         : Started ParkingAiApplication in 3.543 seconds
2026-06-28 22:58:08.590  INFO 38492 --- [           main] c.c.parking.tcp.AudioSocketServer        : AudioSocketServer 启动成功，监听端口: 8084
```

### 3.4 配置 SIP 软电话

1. 在手机上下载 **Zoiper** 或 **Linphone** 应用
2. 添加 SIP 账号：
   - **服务器**: 电脑的局域网 IP（如 `192.168.1.100`）
   - **用户名**: `1000`
   - **密码**: `1234`
3. 拨号 `123456` 触发智能客服流程

---

## 4. 核心模块详解

### 4.1 TCP 层 - 音频流处理

#### 4.1.1 AudioSocketServer

**位置**: `com.changping.parking.tcp.AudioSocketServer`

**职责**: 基于 Netty 的 TCP 服务器，监听 8084 端口，处理 FreeSWITCH 的 outbound socket 连接。

**核心功能**:
- 初始化 Netty 服务端，配置 EventLoopGroup
- 添加 StringDecoder/StringEncoder 处理文本协议
- 注册 AudioSocketHandler 处理业务逻辑
- 优雅关闭资源

**关键代码**:
```java
// 服务启动逻辑
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
            pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
            pipeline.addLast(audioSocketHandler);
        }
    });
```

#### 4.1.2 AudioSocketHandler

**位置**: `com.changping.parking.tcp.AudioSocketHandler`

**职责**: 处理 FreeSWITCH outbound socket 协议的核心业务逻辑。

**协议处理流程**:

| 阶段 | 处理内容 | 命令/事件 |
|------|----------|-----------|
| 连接握手 | 解析 connect 事件，创建会话 | `connect` → `myevents` → `answer` |
| 音频传输 | 提取 CHANNEL_DATA 中的 PCM 音频 | `CHANNEL_DATA` |
| 命令发送 | 发送 playback/hangup 命令 | `sendmsg\nplayback::/path/to/file.wav\n\n` |
| 通话结束 | 处理挂断事件，清理资源 | `CHANNEL_HANGUP` |

**音频数据提取**:
```java
// CHANNEL_DATA 事件中提取音频载荷
int contentLength = Integer.parseInt(extractHeaderValue(msg, "Content-Length", "0"));
if (contentLength > 0) {
    int bodyStart = msg.indexOf("\n\n");
    byte[] audioData = extractAudioBytes(msg, bodyStart + 2, contentLength);
    asrService.feedAudio(sessionId, audioData);
}
```

**发送播放命令**:
```java
// 发送 playback 命令到 FreeSWITCH
String command = "sendmsg\n" +
    "call-command: execute\n" +
    "execute-app-name: playback\n" +
    "execute-app-arg: " + filePath + "\n\n";
session.getChannel().writeAndFlush(command);
```

---

### 4.2 语音服务层 - ASR/TTS

#### 4.2.1 AsrService (语音识别)

**接口位置**: `com.changping.parking.speech.AsrService`

**设计模式**: 策略模式，支持 Mock 和阿里云两种实现。

| 实现类 | 启用条件 | 适用场景 |
|--------|----------|----------|
| MockAsrService | `asr.provider: mock` | 本地开发、测试 |
| AliyunAsrService | `asr.provider: aliyun` | 生产环境 |

**核心方法**:

| 方法 | 说明 |
|------|------|
| `startSession(sessionId)` | 为新通话创建 ASR 会话 |
| `feedAudio(sessionId, audioData)` | 向 ASR 引擎喂入 PCM 音频数据 |
| `stopSession(sessionId)` | 结束 ASR 会话 |
| `setResultCallback(callback)` | 设置识别结果回调 |

**识别流程**:
```
feedAudio → 累积音频 → 达到阈值 → 触发回调 → DialogManager 处理
```

#### 4.2.2 TtsService (语音合成)

**接口位置**: `com.changping.parking.speech.TtsService`

**设计模式**: 策略模式，支持 Mock 和阿里云两种实现。

**核心方法**:

| 方法 | 说明 |
|------|------|
| `synthesize(sessionId, text)` | 将文本合成为 WAV 文件，返回文件路径 |
| `getTempDir()` | 获取临时文件目录 |

**合成流程**:
```
文本输入 → TTS API → 音频数据 → 保存为 WAV 文件 → 返回文件路径
```

---

### 4.3 对话层 - 状态机与 RAG

#### 4.3.1 DialogManager (对话状态机)

**位置**: `com.changping.parking.dialog.DialogManager`

**职责**: 管理对话状态流转，协调各模块工作。

**状态定义**:

| 状态 | 说明 | 触发事件 | 下一状态 |
|------|------|----------|----------|
| WAITING_WELCOME | 等待欢迎语播放完成 | 播放完成 | IDENTIFYING_PARK |
| IDENTIFYING_PARK | 等待用户说出停车场名称 | 用户输入 | PARK_CONFIRMATION |
| PARK_CONFIRMATION | 等待用户确认停车场 | "是"→QA_LOOP, "不是"→IDENTIFYING_PARK | QA_LOOP / IDENTIFYING_PARK |
| QA_LOOP | 问答循环 | 用户提问→继续, "再见"→END | QA_LOOP / END |
| END | 对话结束 | - | - |

**核心逻辑**:

```java
// 状态分发处理
switch (session.getState()) {
    case IDENTIFYING_PARK:
        handleIdentifyingPark(session, text);
        break;
    case PARK_CONFIRMATION:
        handleParkConfirmation(session, text);
        break;
    case QA_LOOP:
        handleQaLoop(session, text);
        break;
}
```

**停车场确认机制**:
- 用户说"西关那家" → 匹配到"西关环岛停车场"
- 系统回复："您想咨询的是西关环岛停车场对吗？"
- 用户说"是" → 进入 QA 模式
- 用户说"不是" → 重新询问停车场名称

**意图识别**:
- 肯定回答：是、对、是的、对的、嗯、没错
- 否定回答：不是、不对、不、错了
- 结束对话：再见、拜拜、挂了、谢谢

#### 4.3.2 IntentParser (停车场名模糊匹配)

**位置**: `com.changping.parking.dialog.IntentParser`

**职责**: 根据用户输入文本匹配到具体停车场。

**四级匹配策略**:

| 优先级 | 匹配方式 | 说明 | 示例 |
|--------|----------|------|------|
| 1 | 精确匹配 | 名称完全相等或包含 | "西关环岛停车场" |
| 2 | 别名匹配 | 匹配停车场别名列表 | "西关那家" → P001 |
| 3 | 模糊匹配 | 字符相似度计算 (>50分) | "西关" → P001 |
| 4 | 地标匹配 | 匹配周边地标和地址 | "昌平公园" → P001 |

**相似度计算算法**:
```java
// 字符相似度 = 共同字符数 / 最长字符串长度
int commonChars = 0;
for (char c : input.toCharArray()) {
    if (s2Chars.contains(c)) {
        commonChars++;
    }
}
return (int) (100.0 * commonChars / Math.max(s1.length(), s2.length()));
```

#### 4.3.3 RagService (检索增强生成)

**位置**: `com.changping.parking.dialog.RagService`

**职责**: 根据停车场信息和用户问题生成回答。

**双模式运行**:

| 模式 | 启用条件 | 特点 |
|------|----------|------|
| 规则引擎 | `llm.provider: mock` | 基于关键词匹配，无网络依赖，响应快 |
| LLM | `llm.provider: deepseek/openai` | 自然语言生成，回答更自然 |

**支持的问题类型**:

| 问题类型 | 关键词 | 回答内容 |
|----------|--------|----------|
| 营业时间 | 时间、开门、关门、几点、24小时 | 营业时间 |
| 收费标准 | 收费、价格、多少钱、费用 | 收费标准 |
| 剩余车位 | 车位、有空位、还有位置 | 实时剩余车位 |
| 地址 | 地址、在哪、位置、导航 | 停车场地址 |
| 电话 | 电话、联系方式 | 联系电话 |
| 设施 | 设施、服务、充电桩 | 设施列表 |
| 周边 | 周边、附近、地标 | 周边地标 |

**规则引擎匹配**:
```java
if (matchesPattern(q, "时间|开门|关门|营业|几点")) {
    return String.format("%s的营业时间是%s。", parking.getName(), parking.getOpenTime());
}
```

**LLM 调用流程**:
```
1. 构建上下文（停车场完整信息）
2. 构建 Prompt（限定回答范围）
3. 调用 LLM API
4. 返回回答
```

---

### 4.4 知识库层 - 数据管理

#### 4.4.1 ParkingInfoService

**位置**: `com.changping.parking.knowledge.ParkingInfoService`

**职责**: 停车场数据管理，提供数据查询和实时车位模拟。

**数据来源**: `src/main/resources/parking-data.json`

**核心方法**:

| 方法 | 说明 |
|------|------|
| `getAll()` | 获取所有停车场列表 |
| `getById(id)` | 根据 ID 获取停车场详情 |
| `getAvailableSpaces(parkingId)` | 获取实时剩余车位（模拟） |
| `updateAvailableSpaces(parkingId, count)` | 更新剩余车位数量 |

**实时车位模拟**:
```java
// 每次查询随机波动 -5 到 +5 个车位
int change = ThreadLocalRandom.current().nextInt(-5, 6);
int updated = Math.max(0, Math.min(info.getTotalSpaces(), current + change));
```

#### 4.4.2 ParkingKnowledgeLoader

**位置**: `com.changping.parking.knowledge.ParkingKnowledgeLoader`

**职责**: 应用启动时初始化 Elasticsearch 索引。

**启用条件**: `knowledge.es.enabled: true`

**初始化流程**:
```
1. 读取 parking-data.json
2. 解析为 ParkingInfo 对象列表
3. 批量保存到 Elasticsearch
```

---

## 5. 配置说明

### 5.1 application.yml 完整配置

```yaml
server:
  port: 8080

spring:
  application:
    name: parking-ai-callcenter
  elasticsearch:
    uris: http://localhost:9200

# FreeSWITCH 配置
freeswitch:
  esl:
    host: 127.0.0.1
    port: 8021
    password: ClueCon
    enabled: false  # 是否启用 ESL 管理接口
  socket:
    port: 8084      # 音频流端口

# ASR 语音识别配置
asr:
  provider: mock    # mock / aliyun
  aliyun:
    appKey: your-app-key
    accessKeyId: your-access-key-id
    accessKeySecret: your-access-key-secret

# TTS 语音合成配置
tts:
  provider: mock    # mock / aliyun
  aliyun:
    appKey: your-app-key
    accessKeyId: your-access-key-id
    accessKeySecret: your-access-key-secret
    voice: xiaoyun  # 发音人
    sampleRate: 8000

# LLM 大模型配置
llm:
  provider: mock    # mock / deepseek / openai
  deepseek:
    baseUrl: https://api.deepseek.com
    apiKey: sk-xxx
    model: deepseek-chat
  openai:
    baseUrl: https://api.openai.com/v1
    apiKey: sk-xxx
    model: gpt-3.5-turbo

# 知识库配置
knowledge:
  dataFile: classpath:parking-data.json
  similarityThreshold: 0.7
  vectorTopK: 3
  es:
    enabled: false  # 是否启用 Elasticsearch

# 音频配置
audio:
  tempDir: ./temp/audio
  sampleRate: 8000
  channels: 1
  sampleSizeInBits: 16

# 日志配置
logging:
  level:
    com.changping.parking: DEBUG
    dev.langchain4j: INFO
```

### 5.2 切换到真实云服务

修改 `application.yml`:

```yaml
asr:
  provider: aliyun
  aliyun:
    appKey: 你的阿里云 APP Key
    accessKeyId: 你的阿里云 AccessKey ID
    accessKeySecret: 你的阿里云 AccessKey Secret

tts:
  provider: aliyun
  aliyun:
    appKey: 你的阿里云 APP Key
    accessKeyId: 你的阿里云 AccessKey ID
    accessKeySecret: 你的阿里云 AccessKey Secret
    voice: xiaoyun

llm:
  provider: deepseek
  deepseek:
    baseUrl: https://api.deepseek.com
    apiKey: sk-你的DeepSeek密钥
    model: deepseek-chat
```

### 5.3 启用 Elasticsearch

```yaml
knowledge:
  es:
    enabled: true

spring:
  elasticsearch:
    uris: http://localhost:9200
```

---

## 6. API 接口文档

### 6.1 停车场管理

#### GET /api/parking/list

获取所有停车场列表

**请求**:
```bash
curl http://localhost:8080/api/parking/list
```

**响应**:
```json
[
  {
    "id": "P001",
    "name": "西关环岛停车场",
    "aliases": ["西关那家", "西关停车场"],
    "address": "昌平区城北街道西关路2号",
    "phone": "010-69741234",
    "totalSpaces": 200,
    "availableSpaces": 45,
    "openTime": "06:00-23:00",
    "feeStandard": "首小时5元，之后每小时3元，单日最高40元"
  }
]
```

#### GET /api/parking/{id}

获取单个停车场详情

**请求**:
```bash
curl http://localhost:8080/api/parking/P001
```

---

### 6.2 意图匹配

#### GET /api/intent/match

根据文本匹配停车场

**参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 用户输入文本 |

**请求**:
```bash
curl "http://localhost:8080/api/intent/match?text=西关那家"
```

**响应**:
```json
{
  "input": "西关那家",
  "parkingId": "P001",
  "parkingName": "西关环岛停车场"
}
```

---

### 6.3 RAG 问答

#### GET /api/rag/ask

根据停车场 ID 和问题生成回答

**参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| parkingId | String | 是 | 停车场 ID |
| question | String | 是 | 用户问题 |

**请求**:
```bash
curl "http://localhost:8080/api/rag/ask?parkingId=P001&question=几点关门"
```

**响应**:
```json
{
  "parkingId": "P001",
  "question": "几点关门",
  "answer": "西关环岛停车场的营业时间是06:00-23:00。"
}
```

---

### 6.4 对话流程

#### POST /api/dialog/start

开始新对话

**请求**:
```bash
curl -X POST http://localhost:8080/api/dialog/start
```

**响应**:
```json
{
  "sessionId": "1bedea8c-cdcd-475b-a37d-1f32e6c68daf",
  "welcome": "您好，欢迎致电昌平区智能停车服务系统。请问您想查询哪个停车场？"
}
```

#### POST /api/dialog/send

发送对话消息

**请求体**:
```json
{
  "sessionId": "1bedea8c-cdcd-475b-a37d-1f32e6c68daf",
  "text": "西关那家"
}
```

**响应**:
```json
{
  "sessionId": "1bedea8c-cdcd-475b-a37d-1f32e6c68daf",
  "state": "IDENTIFYING_PARK",
  "answer": "您想咨询的是西关环岛停车场对吗？",
  "currentParkingId": null,
  "currentParkingName": null
}
```

---

### 6.5 会话管理

#### GET /api/sessions

获取当前活跃会话列表

**请求**:
```bash
curl http://localhost:8080/api/sessions
```

**响应**:
```json
{
  "count": 1,
  "sessions": [...]
}
```

---

## 7. 项目结构

```
parking-ai-callcenter/
├── pom.xml                                    # Maven 依赖配置
├── docker-compose.yml                         # Docker 编排文件
├── README.md                                  # 项目说明文档
│
├── src/main/java/com/changping/parking/
│   ├── ParkingAiApplication.java              # Spring Boot 启动类
│   │
│   ├── config/                                # 配置类
│   │   ├── FreeswitchConfig.java              # FreeSWITCH ESL 配置
│   │   └── ElasticsearchConfig.java           # Elasticsearch 配置
│   │
│   ├── tcp/                                   # TCP 通信层
│   │   ├── AudioSocketServer.java             # Netty 音频服务器
│   │   └── AudioSocketHandler.java            # FreeSWITCH socket 协议处理
│   │
│   ├── speech/                                # 语音服务层
│   │   ├── AsrService.java                    # ASR 接口
│   │   ├── MockAsrService.java                # ASR Mock 实现
│   │   ├── AliyunAsrService.java              # ASR 阿里云实现
│   │   ├── TtsService.java                    # TTS 接口
│   │   ├── MockTtsService.java                # TTS Mock 实现
│   │   └── AliyunTtsService.java              # TTS 阿里云实现
│   │
│   ├── dialog/                                # 对话管理层
│   │   ├── DialogManager.java                 # 对话状态机
│   │   ├── IntentParser.java                  # 停车场名模糊匹配
│   │   └── RagService.java                    # 检索增强生成
│   │
│   ├── knowledge/                             # 知识库层
│   │   ├── ParkingInfoService.java            # 停车场数据服务
│   │   ├── ParkingKnowledgeLoader.java        # ES 索引初始化
│   │   └── ParkingElasticsearchRepository.java # ES Repository
│   │
│   ├── model/                                 # 数据模型
│   │   ├── ParkingInfo.java                   # 停车场实体
│   │   ├── CallSession.java                   # 通话会话实体
│   │   ├── CallSessionManager.java            # 会话管理器
│   │   └── DialogState.java                   # 对话状态枚举
│   │
│   ├── controller/                            # 控制器
│   │   └── TestController.java                # 测试 API 控制器
│   │
│   └── llm/                                   # LLM 配置
│       └── LlmConfig.java                     # DeepSeek/OpenAI 配置
│
├── src/main/resources/
│   ├── application.yml                        # 应用配置文件
│   └── parking-data.json                      # 停车场数据（5个示例）
│
├── freeswitch/                                # FreeSWITCH 配置
│   └── conf/
│       ├── directory/default/1000.xml         # 分机配置 (1000/1001)
│       └── dialplan/public/parking_ai.xml     # 拨号计划 (123456)
│
└── temp/                                      # 临时目录（自动创建）
    └── audio/                                 # TTS 音频文件目录
```

---

## 8. 数据模型

### 8.1 ParkingInfo

停车场信息实体，对应 `parking-data.json` 中的数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 停车场唯一标识 (如 P001) |
| name | String | 停车场名称 |
| aliases | List<String> | 别名列表（用于模糊匹配） |
| address | String | 详细地址 |
| phone | String | 联系电话 |
| totalSpaces | Integer | 总车位数 |
| availableSpaces | Integer | 当前可用车位 |
| openTime | String | 营业时间 |
| feeStandard | String | 收费标准描述 |
| feeDetail | Map | 收费详情（结构化） |
| description | String | 停车场描述 |
| facilities | List<String> | 设施列表 |
| nearbyLandmarks | List<String> | 周边地标 |

### 8.2 CallSession

通话会话实体，存储单路通话的状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话唯一标识 |
| callerNumber | String | 主叫号码 |
| calledNumber | String | 被叫号码 |
| channel | Channel | Netty 通道引用 |
| state | DialogState | 当前对话状态 |
| currentParkingId | String | 当前选中的停车场 ID |
| currentParkingName | String | 当前选中的停车场名称 |
| conversationHistory | List<String> | 对话历史记录 |
| startTime | LocalDateTime | 会话开始时间 |
| lastActivityTime | LocalDateTime | 最后活动时间 |
| asrStarted | boolean | ASR 是否已启动 |
| pendingParkingId | String | 待确认的停车场 ID |
| pendingParkingName | String | 待确认的停车场名称 |

### 8.3 DialogState

对话状态枚举。

| 枚举值 | 说明 |
|--------|------|
| WAITING_WELCOME | 等待欢迎语播放 |
| IDENTIFYING_PARK | 识别停车场名称 |
| PARK_CONFIRMATION | 等待停车场确认 |
| QA_LOOP | 问答循环 |
| END | 对话结束 |

---

## 9. FreeSWITCH 配置

### 9.1 分机配置

**文件**: `freeswitch/conf/directory/default/1000.xml`

配置内部分机 1000 和 1001，密码均为 1234。

```xml
<user id="1000">
  <params>
    <param name="password" value="1234"/>
  </params>
</user>
```

### 9.2 拨号计划

**文件**: `freeswitch/conf/dialplan/public/parking_ai.xml`

当用户拨打 123456 时，触发智能客服流程：
1. 应答呼叫
2. 播放欢迎语
3. 建立 socket 连接到 Java 应用

```xml
<extension name="parking_ai_callcenter">
  <condition field="destination_number" expression="^123456$">
    <action application="answer"/>
    <action application="sleep" data="500"/>
    <action application="socket" data="host.docker.internal:8084 async full"/>
  </condition>
</extension>
```

---

## 10. 部署方案

### 10.1 开发环境部署

```bash
# 1. 启动基础设施
docker-compose up -d

# 2. 启动应用
mvn spring-boot:run

# 3. 配置 SIP 软电话并测试
```

### 10.2 生产环境部署

```bash
# 1. 打包应用
mvn clean package -DskipTests

# 2. 创建配置文件
mkdir -p /opt/parking-ai/config
cp src/main/resources/application.yml /opt/parking-ai/config/
# 修改配置文件中的云服务密钥

# 3. 启动应用
nohup java -jar target/parking-ai-callcenter-1.0.0.jar \
  --spring.config.location=/opt/parking-ai/config/application.yml \
  > /var/log/parking-ai.log 2>&1 &
```

---

## 11. 可行性分析

### 11.1 技术可行性

| 组件 | 可行性 | 评估 |
|------|--------|------|
| FreeSWITCH | ⭐⭐⭐⭐⭐ | 成熟稳定，文档完善 |
| Netty | ⭐⭐⭐⭐⭐ | 高性能网络框架，社区活跃 |
| 阿里云 ASR/TTS | ⭐⭐⭐⭐⭐ | 官方 SDK 完善，延迟低 |
| DeepSeek LLM | ⭐⭐⭐⭐⭐ | 兼容 OpenAI 接口，价格优惠 |
| Elasticsearch | ⭐⭐⭐⭐⭐ | 全文检索成熟，中文支持好 |

### 11.2 风险评估

| 风险 | 等级 | 应对措施 |
|------|------|----------|
| 音频格式不一致 | 中 | 统一配置 8kHz PCM |
| 网络延迟叠加 | 中 | 使用本地缓存，优化请求链路 |
| ASR 并发限制 | 低 | 监控并发数，按需扩容 |
| LLM 幻觉 | 低 | 使用 RAG 限定回答范围 |

### 11.3 性能预估

| 指标 | 预估 |
|------|------|
| 单路通话响应延迟 | < 2 秒 |
| 支持并发通话数 | 100+ (取决于服务器配置) |
| ASR 识别准确率 | > 95% (阿里云标准) |
| TTS 合成延迟 | < 500ms |

---

## 12. 开发建议

### 12.1 开发步骤

```
阶段1: 本地开发 (Mock 模式)
  ├── 启动应用
  ├── 通过 HTTP API 测试对话流程
  ├── 验证停车场匹配和问答功能
  └── 确保核心逻辑正确

阶段2: 接入语音服务
  ├── 申请阿里云 ASR/TTS 服务
  ├── 配置密钥
  ├── 启动 FreeSWITCH
  ├── 使用 SIP 软电话测试完整流程
  └── 调试音频质量

阶段3: 接入 LLM
  ├── 申请 DeepSeek API 密钥
  ├── 配置 LLM 提供商
  ├── 对比规则引擎和 LLM 的回答质量
  └── 优化 Prompt 模板

阶段4: 可选增强
  ├── 启用 Elasticsearch 提升检索性能
  ├── 接入 Milvus 向量检索
  ├── 添加通话录音功能
  └── 增加数据分析看板
```

### 12.2 调试技巧

1. **查看日志**: 应用启动后日志级别为 DEBUG，可查看详细的对话流程
2. **测试 API**: 使用 curl 或 Postman 测试各接口
3. **模拟对话**: 使用 `/api/dialog/*` 接口模拟完整对话流程
4. **检查音频**: 查看 `temp/audio/` 目录下生成的 TTS 文件

---

## 13. 扩展方向

- [ ] **向量检索**: 接入 Milvus/Chroma，实现语义相似度检索
- [ ] **Function Calling**: 让 LLM 自主调用工具获取实时数据
- [ ] **多渠道接入**: 支持微信小程序、公众号、Web 端
- [ ] **通话录音**: 保存通话录音用于质检和分析
- [ ] **数据分析**: 添加通话量、问题分类、满意度等统计
- [ ] **智能转人工**: 支持转人工坐席功能
- [ ] **预约功能**: 支持车位预约和导航
- [ ] **多语言支持**: 添加英文客服能力
- [ ] **语音唤醒**: 支持语音唤醒和打断功能
- [ ] **Docker 部署**: 提供完整的 Docker 一键部署方案

---

## 附录

### A. 常见问题

**Q: FreeSWITCH 连接失败？**
A: 检查 Docker 服务是否启动，确认端口 5060、8021、8084 是否被占用。

**Q: 音频听不到？**
A: 检查网络是否畅通，确认手机和电脑在同一局域网，检查 SIP 软电话的音频设置。

**Q: 停车场匹配失败？**
A: 检查 `parking-data.json` 是否正确加载，尝试使用更完整的停车场名称。

**Q: ASR 识别不准确？**
A: 检查网络连接，尝试在安静环境下说话，调整麦克风灵敏度。

### B. 联系信息

项目维护: Changping Parking AI Team
