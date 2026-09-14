-- 对已有数据库执行此增量脚本；不会修改或删除现有记录。
use yu_ai_code_mother;

create table if not exists guardrail_audit
(
    id          bigint primary key comment '审计 id',
    appId       bigint not null comment '应用 id',
    userId      bigint not null comment '触发用户 id',
    ruleCode    varchar(64) not null comment '触发规则类型',
    matchedRule varchar(512) not null comment '命中关键词或正则',
    inputText   text not null comment '输入摘要，最多 4000 个 Unicode 码点',
    inputLength int not null comment '原始输入 UTF-16 长度',
    outcome     varchar(32) not null comment '处理结果：BLOCKED',
    message     varchar(512) not null comment '向用户展示的拦截原因',
    createTime  datetime(3) default CURRENT_TIMESTAMP(3) not null,
    index idx_app_time (appId, createTime),
    index idx_user_time (userId, createTime),
    index idx_rule_time (ruleCode, createTime)
) comment '输入护轨拦截审计' collate = utf8mb4_unicode_ci;
