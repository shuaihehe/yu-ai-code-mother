package com.yupi.yuaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/** 输入护轨拦截审计。 */
@Data
@Table("guardrail_audit")
public class GuardrailAudit {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;
    @Column("appId")
    private Long appId;
    @Column("userId")
    private Long userId;
    @Column("ruleCode")
    private String ruleCode;
    @Column("matchedRule")
    private String matchedRule;
    @Column("inputText")
    private String inputText;
    @Column("inputLength")
    private Integer inputLength;
    private String outcome;
    private String message;
    @Column("createTime")
    private LocalDateTime createTime;
}
