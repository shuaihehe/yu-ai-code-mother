package com.yupi.yuaicodemother.service;

import com.yupi.yuaicodemother.ai.guardrail.PromptSafetyInputGuardrail.Violation;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.mapper.GuardrailAuditMapper;
import com.yupi.yuaicodemother.model.entity.GuardrailAudit;
import com.yupi.yuaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GuardrailAuditService {
    @Resource
    private GuardrailAuditMapper guardrailAuditMapper;
    @Resource
    private ChatHistoryService chatHistoryService;

    /** 独立提交，避免后续抛出的拒绝异常回滚审计；三条记录保持原子性。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordBlocked(Long appId, Long userId, String input, Violation violation) {
        // 为超长输入保留有限摘要和原始长度，避免审计本身被超大输入拖垮。
        int end = input.offsetByCodePoints(0, Math.min(input.codePointCount(0, input.length()), 4000));
        String excerpt = input.substring(0, end);
        GuardrailAudit audit = new GuardrailAudit();
        audit.setAppId(appId);
        audit.setUserId(userId);
        audit.setRuleCode(violation.ruleCode());
        audit.setMatchedRule(violation.matchedRule());
        audit.setInputText(excerpt);
        audit.setInputLength(input.length());
        audit.setOutcome("BLOCKED");
        audit.setMessage(violation.message());
        try {
            if (guardrailAuditMapper.insert(audit) != 1) {
                throw new IllegalStateException("审计记录写入失败");
            }
            chatHistoryService.addChatMessage(appId, userId, excerpt, ChatHistoryMessageTypeEnum.USER_REJECTED);
            chatHistoryService.addChatMessage(appId, userId, "❌ " + violation.message(),
                    ChatHistoryMessageTypeEnum.GUARDRAIL);
        } catch (RuntimeException e) {
            // 审计失败仍然拒绝请求；日志不输出用户原文或数据库异常中的参数。
            log.error("护轨审计保存失败，appId={}, userId={}, ruleCode={}, outcome=BLOCKED, errorType={}",
                    appId, userId, violation.ruleCode(), e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,
                    violation.message() + "（拦截记录保存失败）");
        }
    }
}
