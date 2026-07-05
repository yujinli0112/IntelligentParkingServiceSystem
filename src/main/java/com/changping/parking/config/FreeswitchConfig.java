package com.changping.parking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * FreeSWITCH ESL（Event Socket Library）配置类
 * 
 * 通过原始 Socket 连接到 FreeSWITCH 的 ESL 端口，接收通话事件。
 * 使用条件：配置 `freeswitch.esl.enabled=true`
 * 
 * 注意：这是一个可选功能，系统主要通过 outbound socket 接收音频数据，
 * ESL 用于接收通话状态事件（如通话开始、结束等）。
 * 
 * @author Changping Parking AI Team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "freeswitch.esl.enabled", havingValue = "true")
public class FreeswitchConfig {

    /** ESL 服务器地址 */
    @Value("${freeswitch.esl.host:127.0.0.1}")
    private String eslHost;

    /** ESL 服务器端口（默认 8021） */
    @Value("${freeswitch.esl.port:8021}")
    private int eslPort;

    /** ESL 认证密码（默认 ClueCon） */
    @Value("${freeswitch.esl.password:ClueCon}")
    private String eslPassword;

    /** Socket 连接 */
    private Socket socket;

    /** 输出流，用于发送命令 */
    private PrintWriter out;

    /** 输入流，用于接收响应和事件 */
    private BufferedReader in;

    /**
     * 初始化方法，建立 ESL 连接并订阅事件
     */
    @PostConstruct
    public void connect() {
        try {
            socket = new Socket(eslHost, eslPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            StringBuilder authBuffer = new StringBuilder();
            while ((line = in.readLine()) != null) {
                authBuffer.append(line).append("\n");
                if (line.contains("Content-Type: auth/request")) {
                    break;
                }
            }

            out.println("auth " + eslPassword);
            out.println();

            StringBuilder authResp = new StringBuilder();
            while ((line = in.readLine()) != null) {
                authResp.append(line).append("\n");
                if (line.contains("Reply-Text: +OK accepted")) {
                    log.info("FreeSWITCH ESL 连接成功: {}:{}", eslHost, eslPort);
                    break;
                }
                if (authResp.toString().contains("-ERR")) {
                    log.error("FreeSWITCH ESL 认证失败");
                    return;
                }
            }

            out.println("event plain all");
            out.println();
            log.info("已订阅 FreeSWITCH 事件");

            startEventListener();

        } catch (Exception e) {
            log.warn("FreeSWITCH ESL 连接失败（可选功能）: {}", e.getMessage());
        }
    }

    /**
     * 启动事件监听线程
     * 
     * 持续读取 ESL 事件并记录日志。
     */
    private void startEventListener() {
        new Thread(() -> {
            try {
                String line;
                StringBuilder eventBuffer = new StringBuilder();
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (eventBuffer.length() > 0) {
                            String event = eventBuffer.toString();
                            if (event.contains("Event-Name:")) {
                                log.debug("ESL 事件: {}", event.substring(0, Math.min(200, event.length())));
                            }
                            eventBuffer.setLength(0);
                        }
                    } else {
                        eventBuffer.append(line).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("ESL 事件监听线程结束: {}", e.getMessage());
            }
        }, "freeswitch-esl-listener").start();
    }

    /**
     * 发送 ESL 命令
     * 
     * @param command ESL 命令字符串
     */
    public void sendCommand(String command) {
        if (out != null) {
            out.println(command);
            out.println();
            log.debug("发送 ESL 命令: {}", command);
        }
    }
}
