# Debug Session: freeswitch-socket-1000

**Status**: [OPEN]
**Started**: 2026-07-04
**Symptom**: FreeSWITCH 拨打 1000 后，Java 服务 8084 端口 25 秒后收到 disconnect-notice，欢迎语未能播放

## 已知证据

- FreeSWITCH 5060 端口监听正常
- Java 服务 8084 端口监听正常
- 拨号计划 `parking_ai_service` 被正确匹配（log 显示 Regex PASS）
- FreeSWITCH 发起 TCP 连接到 8084 成功（connect log 出现）
- Java 服务约 25 秒后收到 disconnect-notice
- TTS 合成实际已执行（log 显示 "SAPI TTS 合成完成"）
- 配置文件实际未读取，IP 仍是默认 127.0.0.1（fs_cli 显示）

## 假设列表

1. **H1**: FreeSWITCH 配置文件 `internal.xml` / `external.xml` 中存在多组 `sip-ip` / `rtp-ip` 配置，fs_cli 显示的 IP `127.0.0.1` 来自未被修改的副本 → 重新加载未生效
2. **H2**: `mod_event_socket` 在 25 秒后触发 Socket Error 是因为 Java 端没有及时发送正确的 handshake 响应（`connect` 命令需带 Content-Type 头）
3. **H3**: Java 端在 `connect` 后立即执行 `answer`，但 session 还没建立完成，发送命令丢失
4. **H4**: FreeSWITCH 的 outbound socket `full` 模式要求 Java 端必须先发送一个空行作为心跳，否则 25 秒后会断开
5. **H5**: dialplan 中 `socket` app 的 `async` 改为 `sync` 后，由于 Java 端没有发送正确的命令协议，导致 FreeSWITCH 认为挂起
