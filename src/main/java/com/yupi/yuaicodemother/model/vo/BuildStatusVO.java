package com.yupi.yuaicodemother.model.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 构建状态快照。buildId 标识一次生成 / 构建任务。 */
@Data
public class BuildStatusVO {
    private Long appId;
    private String buildId;
    private String status;
    private String message;
    @JsonProperty("isBuilding")
    private boolean building;
    private boolean projectExists;
    private boolean distExists;
    private Long buildTime;
    private Long startedAt;
    private Long finishedAt;
}
