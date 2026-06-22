package com.industrial.agent.eval;

import com.industrial.agent.agent.router.Intent;
import java.util.List;

public class EvalDataset {

    public static List<EvalCase> load() {
        return List.of(
                // ALARM cases
                new EvalCase("A01", "CNC-001 有什么告警？", Intent.ALARM, List.of("告警", "CNC-001")),
                new EvalCase("A02", "查一下所有设备的报警信息", Intent.ALARM, List.of("告警", "设备")),
                new EvalCase("A03", "CNC-002 现在有没有警告", Intent.ALARM, List.of("CNC-002")),
                new EvalCase("A04", "设备告警清单", Intent.ALARM, List.of("告警")),

                // DATA cases
                new EvalCase("D01", "CNC-001 最近一小时的温度趋势", Intent.DATA, List.of("温度", "CNC-001")),
                new EvalCase("D02", "查一下振动数据", Intent.DATA, List.of("振动")),
                new EvalCase("D03", "ROBOT-ARM-A1 实时压力是多少", Intent.DATA, List.of("压力")),
                new EvalCase("D04", "最近5分钟的设备遥测数据", Intent.DATA, List.of("数据")),

                // DIAGNOSIS cases
                new EvalCase("DG01", "CNC-001 振动异常，做完整诊断", Intent.DIAGNOSIS, List.of("诊断", "振动")),
                new EvalCase("DG02", "帮我排查一下 CNC-001 的故障", Intent.DIAGNOSIS, List.of("故障", "CNC-001")),
                new EvalCase("DG03", "设备温度过高需要检修吗", Intent.DIAGNOSIS, List.of("温度")),
                new EvalCase("DG04", "CNC-001 振动超标，创建维修工单", Intent.DIAGNOSIS, List.of("工单")),

                // KNOWLEDGE cases
                new EvalCase("K01", "轴承温度过高一般怎么修？", Intent.KNOWLEDGE, List.of("轴承", "温度")),
                new EvalCase("K02", "电机振动异常的常见原因", Intent.KNOWLEDGE, List.of("振动", "原因")),
                new EvalCase("K03", "维修手册里关于润滑的操作规程", Intent.KNOWLEDGE, List.of("润滑")),
                new EvalCase("K04", "齿轮箱异响怎么处理", Intent.KNOWLEDGE, List.of("齿轮")),

                // GENERAL cases
                new EvalCase("G01", "你好，你是谁？", Intent.GENERAL, List.of("助手")),
                new EvalCase("G02", "今天天气怎么样", Intent.GENERAL, List.of()),
                new EvalCase("G03", "谢谢你的帮助", Intent.GENERAL, List.of()),
                new EvalCase("G04", "工业 Agent 是什么意思", Intent.GENERAL, List.of("Agent"))
        );
    }
}
