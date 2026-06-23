package com.industrial.agent.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OutputGuard {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)(api.?key|密码|password|secret|token)\\s*[:=]\\s*\\S+"),
            Pattern.compile("(?i)(DROP\\s+TABLE|DELETE\\s+FROM|TRUNCATE)")
    );

    public GuardResult check(String output) {
        if (output == null || output.isBlank()) {
            return GuardResult.blocked("输出为空");
        }
        for (Pattern p : SENSITIVE_PATTERNS) {
            if (p.matcher(output).find()) {
                log.warn("[OutputGuard] BLOCKED sensitive output detected");
                return GuardResult.blocked("输出包含敏感信息，已过滤");
            }
        }
        return GuardResult.passed();
    }
}
