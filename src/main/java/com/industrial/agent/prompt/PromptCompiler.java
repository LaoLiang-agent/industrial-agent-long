package com.industrial.agent.prompt;

import com.industrial.agent.config.AgentPromptProperties;
import com.industrial.agent.memory.MemoryManager;
import com.industrial.agent.runtime.RuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Six-layer prompt compiler. Replaces the hard-coded {@code @SystemMessage}
 * string with a layered assembly:
 *
 * <pre>
 *   L1 Role     — fixed identity, from agent.prompt.role
 *   L2 Policy   — safety/compliance rules, from PolicyRegistry
 *   L3 Contract — tool governance envelope, from ToolContractGenerator
 *   L4 Memory   — merged L4+L3+L2 memory context, from MemoryManager
 *   L5 Knowledge— RAG retrieval (hook; wired in P2)
 *   L6 Task     — ReAct workflow + output format + the user message
 * </pre>
 *
 * <p>L1–L5 compile into the system message ({@link #compileSystem}); L6 wraps the
 * user turn ({@link #compileTask}). Splitting them keeps the per-request user
 * message distinct from the stable operating context.
 */
@Slf4j
@Component
public class PromptCompiler {

    private final AgentPromptProperties properties;
    private final PolicyRegistry policyRegistry;
    private final ToolContractGenerator toolContract;
    private final MemoryManager memory;

    public PromptCompiler(AgentPromptProperties properties, PolicyRegistry policyRegistry,
                          ToolContractGenerator toolContract, MemoryManager memory) {
        this.properties = properties;
        this.policyRegistry = policyRegistry;
        this.toolContract = toolContract;
        this.memory = memory;
    }

    /** L1–L5 → system message. L5 knowledge is a hook until RAG lands in P2. */
    public String compileSystem(RuntimeContext ctx) {
        return compileSystem(ctx, "");
    }

    public String compileSystem(RuntimeContext ctx, String knowledge) {
        StringBuilder sb = new StringBuilder();

        // L1 Role
        sb.append("【角色】\n").append(properties.getRole()).append("\n")
          .append("你的知识有限，无法访问实时设备数据；涉及设备状态、告警、历史数据、故障原因时必须调用工具查询，不得凭猜测回答。\n\n");

        // L2 Policy
        List<String> policies = policyRegistry.policies();
        if (!policies.isEmpty()) {
            sb.append("【安全策略】(必须严格遵守)\n");
            for (String p : policies) {
                sb.append("- ").append(p).append('\n');
            }
            sb.append('\n');
        }

        // L3 Tool Contract
        String contract = toolContract.generate();
        if (!contract.isBlank()) {
            sb.append("【可用工具与调用约束】\n").append(contract).append('\n');
        }

        // L4 Memory Context
        String memoryBlock = memory.buildContextBlock(ctx);
        if (!memoryBlock.isBlank()) {
            sb.append("【已知上下文】(供参考，勿复述)\n").append(memoryBlock).append('\n');
        }

        // L5 Knowledge Context (RAG) — hook, empty until P2
        if (knowledge != null && !knowledge.isBlank()) {
            sb.append("【知识库检索】\n").append(knowledge).append('\n');
        }

        return sb.toString();
    }

    /** L6 → task instruction: ReAct workflow + output format + user message. */
    public String compileTask(String userMessage) {
        return """
                思考路径（ReAct），每步先思考再行动：
                1. 数据采集：先用 queryDeviceAlarms 和 queryDeviceHistory 获取实时数据
                2. 知识检索：如有异常，用 searchKnowledgeBase 检索相关维修知识和历史案例
                3. 诊断分析：用 generateDiagnosis 分析根因
                4. 工单决策：确认是硬件故障需要人工介入时，用 createWorkOrder 创建工单；设备正常无告警则不要创建
                5. 最终输出：按「设备状态 → 异常发现 → 诊断结论 → 维修建议 → 工单信息」结构呈现

                回复规范：
                - 用结构化方式呈现结果（表格优先于纯文本）
                - 涉及安全风险时明确标注优先级（HIGH/MEDIUM/LOW）
                - 不确定时如实说明，不要编造数据
                ---
                用户问题：%s""".formatted(userMessage);
    }
}
