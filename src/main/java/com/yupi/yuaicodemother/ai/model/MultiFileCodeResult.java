package com.yupi.yuaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成多文件代码结果")
@Data
public class MultiFileCodeResult {

    /**
     * HTML代码
     */
    @Description("HTML代码")
    private String htmlCode;

    /**
     * CSS代码
     */
    @Description("CSS代码")
    private String cssCode;

    /**
     * JS代码
     */
    @Description("JS代码")
    private String jsCode;

    /**
     * 描述
     */
    @Description("生成的代码描述")
    private String description;
}
