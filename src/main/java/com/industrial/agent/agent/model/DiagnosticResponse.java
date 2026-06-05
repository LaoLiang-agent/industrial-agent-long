package com.industrial.agent.agent.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import java.util.List;

@Data
public class DiagnosticResponse {

    @Description("设备ID")
    private String deviceId;

    @Description("设备当前状态：normal/warning/critical")
    private String status;

    @Description("诊断分析结论")
    private String analysis;

    @Description("可能的故障原因，按可能性从高到低排列")
    private List<String> possibleCauses;

    @Description("建议的维修或处理措施")
    private List<String> suggestedActions;

    @Description("优先级：HIGH/MEDIUM/LOW")
    private String priority;

    @Description("是否需要立即处理")
    private Boolean requiresImmediateAction;

    @Description("诊断置信度，0.0-1.0")
    private Double confidence;
}
