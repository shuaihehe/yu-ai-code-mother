package com.yupi.yuaicodemother.core.builder;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 *
 */
@Slf4j
@Component
public class VueProjectBuilder {

    @Resource
    private BuildStatusStore buildStatusStore;

    /**
     * 异步构建项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        String buildId = buildStatusStore.start(projectPath, "building");
        startBuildThread(projectPath, buildId);
    }

    public void buildGeneratedProjectAsync(String projectPath, String buildId) {
        if (buildStatusStore.transition(projectPath, buildId, "generating", "building", "代码已生成，正在构建 Vue 项目...")) {
            startBuildThread(projectPath, buildId);
        }
    }

    private void startBuildThread(String path, String buildId) {
        try {
            Thread.ofVirtual().name("vue-builder-" + buildId).start(() -> runBuild(path, buildId));
        } catch (RuntimeException e) {
            buildStatusStore.transition(path, buildId, "building", "failed", "无法启动构建任务，请重试");
            throw e;
        }
    }


    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        String buildId = buildStatusStore.start(projectPath, "building");
        return runBuild(projectPath, buildId);
    }

    private boolean runBuild(String path, String buildId) {
        try {
            String failure = buildFiles(path);
            boolean success = failure == null;
            buildStatusStore.transition(path, buildId, "building", success ? "completed" : "failed",
                    success ? "构建已完成" : failure);
            return success;
        } catch (Exception e) {
            log.error("构建 Vue 项目失败: {}", path, e);
            buildStatusStore.transition(path, buildId, "building", "failed", "构建发生异常，请检查后端日志后重试");
            return false;
        }
    }

    private String buildFiles(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return "项目目录不存在";
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return "项目缺少 package.json";
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        // 执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return "依赖安装失败或超时，请检查后端日志";
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return "项目打包失败或超时，请检查后端日志";
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist/index.html");
        if (!distDir.isFile()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return "构建未生成 dist/index.html";
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return null;
    }


    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        return executeCommand(projectDir, "npm install", 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        return executeCommand(projectDir, "npm run build", 180); // 3分钟超时
    }


    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            // 直接消费子进程输出，避免 npm 日志填满管道后一直等待。
            Process process = new ProcessBuilder(command.split("\\s+"))
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

}
