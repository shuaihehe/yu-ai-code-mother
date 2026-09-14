package com.yupi.yuaicodemother.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        Violation violation = check(userMessage.singleText());
        return violation == null ? success() : fatal(violation.message());
    }

    /** 与业务入口共用规则，避免审计检查和 AI 护轨的判断不一致。 */
    public static Violation check(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new Violation("EMPTY_INPUT", "blank", "输入内容不能为空");
        }
        // 检查输入长度
        if (input.length() > 1000) {
            return new Violation("INPUT_TOO_LONG", "maxLength=1000", "输入内容过长，不要超过 1000 字");
        }
        // 检查敏感词
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase(Locale.ROOT))) {
                return new Violation("SENSITIVE_WORD", sensitiveWord, "输入包含不当内容，请修改后重试");
            }
        }
        // 检查注入攻击模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return new Violation("PROMPT_INJECTION", pattern.pattern(), "检测到恶意输入，请求被拒绝");
            }
        }
        return null;
    }

    public record Violation(String ruleCode, String matchedRule, String message) {}
}
