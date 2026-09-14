package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.yuaicodemother.ai.AiCodeGenTypeRoutingService;
import com.yupi.yuaicodemother.ai.guardrail.PromptSafetyInputGuardrail;
import com.yupi.yuaicodemother.constant.AppConstant;
import com.yupi.yuaicodemother.core.AiCodeGeneratorFacade;
import com.yupi.yuaicodemother.core.builder.VueProjectBuilder;
import com.yupi.yuaicodemother.core.builder.BuildStatusStore;
import com.yupi.yuaicodemother.core.handler.StreamHandlerExecutor;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.exception.ThrowUtils;
import com.yupi.yuaicodemother.mapper.AppMapper;
import com.yupi.yuaicodemother.model.dto.app.AppAddRequest;
import com.yupi.yuaicodemother.model.dto.app.AppQueryRequest;
import com.yupi.yuaicodemother.model.entity.App;
import com.yupi.yuaicodemother.model.entity.User;
import com.yupi.yuaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.yupi.yuaicodemother.model.vo.AppVO;
import com.yupi.yuaicodemother.model.vo.UserVO;
import com.yupi.yuaicodemother.service.AppService;
import com.yupi.yuaicodemother.service.ChatHistoryService;
import com.yupi.yuaicodemother.service.GuardrailAuditService;
import com.yupi.yuaicodemother.service.ScreenshotService;
import com.yupi.yuaicodemother.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    private static final Set<String> SORT_FIELD_SET = Set.of(
            "id", "appName", "cover", "initPrompt", "codeGenType", "deployKey",
            "deployedTime", "priority", "userId", "editTime", "createTime", "updateTime"
    );

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private GuardrailAuditService guardrailAuditService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private BuildStatusStore buildStatusStore;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        CodeGenTypeEnum selectCodeGenTypeEnum = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectCodeGenTypeEnum.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectCodeGenTypeEnum.getValue());
        return app.getId();
    }


    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId);
        if (StrUtil.isNotBlank(sortField) && SORT_FIELD_SET.contains(sortField)) {
            boolean isAsc = "ascend".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
            queryWrapper.orderBy(sortField, isAsc);
        }
        return queryWrapper;
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用id错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "代码生成类型错误");
        // 订阅时占用本轮任务，防止多窗口在同一项目上交叉生成 / 构建。
        return Flux.defer(() -> {
            // 在创建 AI 服务、加载记忆或启动构建前拦截，保留完整的用户和应用上下文。
            var violation = PromptSafetyInputGuardrail.check(message);
            if (violation != null) {
                guardrailAuditService.recordBlocked(appId, loginUser.getId(), message, violation);
                return Flux.error(new BusinessException(ErrorCode.FORBIDDEN_ERROR, violation.message()));
            }
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
            boolean vueProject = codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT;
            String buildId = vueProject ? buildStatusStore.start(projectPath, "generating") : null;
            try {
                chatHistoryService.addChatMessage(appId, loginUser.getId(), message,
                        ChatHistoryMessageTypeEnum.USER);
                Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
                Flux<String> result = streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
                if (!vueProject) return result;
                // 必须先记录 building，再让控制器发送 done；npm 仍在后台运行。
                return result.concatWith(Mono.<String>fromRunnable(() ->
                                vueProjectBuilder.buildGeneratedProjectAsync(projectPath, buildId)))
                        .doOnError(error -> buildStatusStore.transition(projectPath, buildId, "generating", "failed", "代码生成失败，请重试"))
                        // TokenStream 无法随 SSE 断开可靠取消：保留后台订阅直至结束，
                        // 防止刷新后释放任务锁，而旧工具仍在写文件。仅缓存终止信号。
                        .cache(0);
            } catch (Exception e) {
                if (vueProject) buildStatusStore.transition(projectPath, buildId, "generating", "failed", "代码生成失败，请重试");
                return Flux.error(e);
            }
        });

    }

    /**
     * 删除应用时一并删除该应用的全部对话历史。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        Long appId;
        try {
            appId = Long.valueOf(id.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 错误");
        }
        ThrowUtils.throwIf(appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        chatHistoryService.removeByAppId(appId);
        boolean result = super.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        return true;
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // 5. 获取代码生成类型，获取原始代码生成路径（应用访问目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/" + sourceDirName;
        // 6. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在,请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean builtSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!builtSuccess, ErrorCode.SYSTEM_ERROR, "构建 Vue 项目失败");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + "/" + deployKey;
        File deployDir = new File(deployDirPath);
        try {
            FileUtil.copyContent(sourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复制文件失败");
        }
        // 9. SCP 上传到云服务器
        try {
            Process process = new ProcessBuilder(
                    "scp", "-r", deployDir.getAbsolutePath(), AppConstant.CODE_DEPLOY_TARGET
            ).inheritIO().start();
            int exitCode = process.waitFor();
            ThrowUtils.throwIf(exitCode != 0, ErrorCode.SYSTEM_ERROR, "SCP 上传失败");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SCP 上传失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SCP 上传被中断");
        }
        // 10. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新数据库失败");
        // 11. 得到可访问的 URL 地址
        String appDeployUrl = String.format("%s/%s", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 12. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成截图并且更新应用封面
     * @param appId 应用id
     * @param appDeployUrl 应用部署地址
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appDeployUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appDeployUrl);
            // 更新数据库封面
            App app = new App();
            app.setId(appId);
            app.setCover(screenshotUrl);
            boolean updateById = this.updateById(app);
            ThrowUtils.throwIf(!updateById, ErrorCode.OPERATION_ERROR, "更新数据库封面字段失败");
        });
    }

    /**
     * 保存 AI 的完整回复。空回复按失败处理，并让 SSE 流进入异常分支。
     */
    private void saveAiResponse(Long appId, Long userId, String aiResponse,
                                AtomicBoolean aiHistorySaved) {
        if (StrUtil.isBlank(aiResponse)) {
            saveAiErrorMessage(appId, userId, "AI 未返回有效回复", aiHistorySaved);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 未返回有效回复");
        }
        if (!aiHistorySaved.compareAndSet(false, true)) {
            return;
        }
        try {
            chatHistoryService.addChatMessage(appId, userId, aiResponse,
                    ChatHistoryMessageTypeEnum.AI);
        } catch (RuntimeException e) {
            aiHistorySaved.set(false);
            throw e;
        }
    }

    /**
     * 保存 AI 回复失败信息。记录失败本身不能覆盖原始 AI 异常。
     */
    private void saveAiErrorMessage(Long appId, Long userId, Throwable error,
                                    AtomicBoolean aiHistorySaved) {
        String errorDetail = error == null || StrUtil.isBlank(error.getMessage())
                ? "未知错误"
                : error.getMessage();
        saveAiErrorMessage(appId, userId, "AI 回复失败：" + errorDetail, aiHistorySaved);
    }

    private void saveAiErrorMessage(Long appId, Long userId, String errorMessage,
                                    AtomicBoolean aiHistorySaved) {
        if (!aiHistorySaved.compareAndSet(false, true)) {
            return;
        }
        try {
            chatHistoryService.addChatMessage(appId, userId, errorMessage,
                    ChatHistoryMessageTypeEnum.AI);
        } catch (RuntimeException persistenceError) {
            aiHistorySaved.set(false);
            log.error("保存 AI 错误消息失败，appId：{}，userId：{}", appId, userId,
                    persistenceError);
        }
    }

}
