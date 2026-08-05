package com.yupi.yuaicodemother.core.parser;

import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;

/**
 * 代码解析器执行器
 * 根据代码生成类型执行对应的代码解析器策略
 *
 * @author yupi
 */
public class CodeParserExecutor {

    public static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    public static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     *
     * @param content          代码内容
     * @param codeGenTypeEnum  代码生成类型
     * @return 解析结果
     */
    public static Object executeParse(String content, CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeParser.parseCode(content);
            case MULTI_FILE -> multiFileCodeParser.parseCode(content);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型");
        };

    }
}
