package com.yupi.yuaicodemother.core;

import com.yupi.yuaicodemother.ai.AiCodeGeneratorService;
import com.yupi.yuaicodemother.ai.model.HtmlCodeResult;
import com.yupi.yuaicodemother.ai.model.MultiFileCodeResult;
import com.yupi.yuaicodemother.core.parser.CodeParserExecutor;
import com.yupi.yuaicodemother.core.saver.CodeFileSaverExecutor;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成器门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：根据类型生成代码并保存
     * @param userMessage 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @param appId 应用 id
     * @return 保存的文件路径
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            // case HTML -> yield generateAndSaveHtmlCode(userMessage);
            case HTML:
                yield generateAndSaveHtmlCode(userMessage, appId);
            case MULTI_FILE:
                yield generateAndSaveMultiFileCode(userMessage, appId);
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 统一入口：根据类型生成代码并保存 (流式)
     * @param userMessage 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @param appId 应用 id
     * @return 返回流式代码片段
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            // case HTML -> yield generateAndSaveHtmlCode(userMessage);
            case HTML:
                yield generateAndSaveHtmlCodeStream(userMessage, appId);
            case MULTI_FILE:
                yield generateAndSaveMultiFileCodeStream(userMessage, appId);
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 根据用户提示词生成 HTML 代码并保存 (流式)
     * @param userMessage 用户提示词
     * @return 返回流式代码片段
     */
    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage, Long appId) {
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        return processCodeStream(result, CodeGenTypeEnum.HTML, appId);
    }

    /**
     * 处理代码流式返回，并保存代码
     * @param codeStream 代码流
     * @param codeGenTypeEnum 代码生成类型
     * @return 返回处理后的代码流
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // 字符串拼接器，用户当流式返回所有的代之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parseResult = CodeParserExecutor.executeParse(completeCode, codeGenTypeEnum);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parseResult, codeGenTypeEnum, appId);
                log.info("保存成功，保存目录：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存代码时出错：{}", e.getMessage());
            }
        });
    }


    /**
     * 根据用户提示词生成多文件代码并保存 (流式)
     * @param userMessage 用户提示词
     * @return 返回流式代码片段
     */
    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage, Long appId) {
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        return processCodeStream(result, CodeGenTypeEnum.MULTI_FILE, appId);
    }

    /**
     * 处理非流式代码生成结果，并保存代码
     * @param codeResult      代码生成结果对象
     * @param codeGenTypeEnum  代码生成类型
     * @return 保存的文件目录
     */
    private File processCode(Object codeResult, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        File saveDir = CodeFileSaverExecutor.executeSaver(codeResult, codeGenTypeEnum, appId);
        log.info("保存成功，保存目录：{}", saveDir.getAbsolutePath());
        return saveDir;
    }

    private File generateAndSaveHtmlCode(String userMessage, Long appId) {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return processCode(htmlCodeResult, CodeGenTypeEnum.HTML, appId);
    }

    private File generateAndSaveMultiFileCode(String userMessage, Long appId) {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return processCode(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE, appId);
    }
}
