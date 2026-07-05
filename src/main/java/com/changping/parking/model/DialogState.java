package com.changping.parking.model;

/**
 * 对话状态枚举
 * 
 * 定义对话状态机的各个状态，控制对话流程的流转。
 * 
 * 状态流转图：
 * <pre>
 * WAITING_WELCOME → IDENTIFYING_PARK → PARK_CONFIRMATION → QA_LOOP → END
 *                       ↑                           │
 *                       │                           │
 *                       └───────────────────────────┘ (用户说"不是"时回退)
 * </pre>
 * 
 * @author Changping Parking AI Team
 */
public enum DialogState {

    /** 等待欢迎语播放完成 */
    WAITING_WELCOME,

    /** 识别停车场名称阶段，等待用户说出停车场名称 */
    IDENTIFYING_PARK,

    /** 停车场确认阶段，等待用户确认"是"或"不是" */
    PARK_CONFIRMATION,

    /** 问答循环阶段，用户可以自由提问关于停车场的问题 */
    QA_LOOP,

    /** 对话结束 */
    END
}
