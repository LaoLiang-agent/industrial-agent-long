package com.industrial.agent.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DiagnosisTool {

    @Tool("基于设备的告警信息和历史数据，生成故障诊断结论和维修建议。输入为告警类型和关键指标异常值，返回诊断报告。")
    public String generateDiagnosis(String alarmType, String abnormalMetrics) {
        log.info("[DiagnosisTool] Generating diagnosis for alarm: {}, metrics: {}", alarmType, abnormalMetrics);

        // Knowledge base lookup would go here — RAG retrieval from maintenance manuals
        // For now, rule-based simulation
        String diagnosis = switch (alarmType) {
            case "温度过高" -> "可能原因：1) 冷却系统故障 2) 轴承润滑不足 3) 负载异常偏高。" +
                    "建议：检查冷却液液位和循环泵，检查轴承油脂状态，确认当前负载是否超过额定值80%。";
            case "振动异常" -> "可能原因：1) 轴承磨损 2) 转子不平衡 3) 联轴器对中偏差。" +
                    "建议：进行振动频谱分析，检查轴承间隙，校验联轴器对中参数。";
            case "压力超标" -> "可能原因：1) 管路堵塞 2) 阀门开度异常 3) 泵速过高。" +
                    "建议：检查管路过滤器，确认阀门位置，校验泵速设定值。";
            case "电流过载" -> "可能原因：1) 机械卡阻 2) 电源电压波动 3) 电机绕组故障。" +
                    "建议：检查机械传动部件，测量电源电压稳定性，进行电机绝缘测试。";
            default -> "建议人工现场排查，必要时联系设备厂商技术支持。";
        };

        return String.format(
                "{\"alarmType\":\"%s\",\"diagnosis\":\"%s\",\"confidence\":0.85,\"priority\":\"HIGH\"}",
                alarmType, diagnosis
        );
    }
}
