package com.industrial.agent.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class InputGuard {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("(?i)(删除|drop|truncate|shutdown|reboot).*数据库"),
            Pattern.compile("(?i)(rm\\s+-rf|format|fdisk)"),
            Pattern.compile("(?i)ignore.*safety|bypass.*guard|绕过.*护栏")
    );

    private static final int MAX_INPUT_LENGTH = 2000;

    public GuardResult check(String input) {
        if (input == null || input.isBlank()) {
            return GuardResult.blocked("输入为空");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            return GuardResult.blocked("输入超长（最大 " + MAX_INPUT_LENGTH + " 字符）");
        }
        for (Pattern p : BLOCKED_PATTERNS) {
            if (p.matcher(input).find()) {
                log.warn("[InputGuard] BLOCKED dangerous input: {}", input.substring(0, Math.min(50, input.length())));
                return GuardResult.blocked("检测到危险指令");
            }
        }
        return GuardResult.passed();
    }
}
