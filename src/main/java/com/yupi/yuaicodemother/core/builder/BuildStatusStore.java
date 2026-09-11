package com.yupi.yuaicodemother.core.builder;

import cn.hutool.json.JSONUtil;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.model.vo.BuildStatusVO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 单实例构建协调；状态文件放在项目目录旁，不进入 dist 或源码下载。 */
@Component
public class BuildStatusStore {
    private final String instanceId = UUID.randomUUID().toString();

    @Data
    public static class State {
        private String instanceId;
        private String buildId;
        private String status;
        private String message;
        private Long startedAt;
        private Long finishedAt;
    }

    public synchronized String start(String projectPath, String status) {
        State previous = read(projectPath);
        if (previous != null && active(previous.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用正在生成或构建，请等待完成后重试");
        }
        State state = new State();
        state.setInstanceId(instanceId);
        state.setBuildId(UUID.randomUUID().toString());
        state.setStatus(status);
        state.setMessage("generating".equals(status) ? "AI 正在生成网站..." : "代码已生成，正在构建 Vue 项目...");
        state.setStartedAt(System.currentTimeMillis());
        write(projectPath, state);
        return state.getBuildId();
    }

    public synchronized boolean transition(String path, String buildId, String expected, String status, String message) {
        State state = read(path);
        if (state == null || !buildId.equals(state.getBuildId()) || !expected.equals(state.getStatus())) return false;
        state.setStatus(status);
        state.setMessage(message);
        if (!active(status)) state.setFinishedAt(System.currentTimeMillis());
        write(path, state);
        return true;
    }

    public synchronized BuildStatusVO getStatus(String path) {
        State state = read(path);
        BuildStatusVO result = new BuildStatusVO();
        result.setProjectExists(Files.isDirectory(Path.of(path)));
        result.setDistExists(Files.isRegularFile(Path.of(path, "dist", "index.html")));
        if (state == null) {
            // 历史项目只能确认存在可浏览的产物，不能声称某次构建成功。
            result.setStatus(result.isDistExists() ? "ready" : result.isProjectExists() ? "pending" : "not_found");
            result.setMessage(result.isDistExists() ? "已有历史预览可用" : "尚无构建记录，请生成网站");
            return result;
        }
        result.setBuildId(state.getBuildId());
        result.setStatus(state.getStatus());
        result.setMessage(state.getMessage());
        result.setBuilding("building".equals(state.getStatus()));
        result.setStartedAt(state.getStartedAt());
        result.setFinishedAt(state.getFinishedAt());
        if ("completed".equals(state.getStatus())) result.setBuildTime(state.getFinishedAt());
        return result;
    }

    private boolean active(String status) {
        return "generating".equals(status) || "building".equals(status);
    }

    private Path statusFile(String path) {
        return Path.of(path).toAbsolutePath().normalize().resolveSibling(Path.of(path).getFileName() + ".build-status.json");
    }

    private State read(String path) {
        Path file = statusFile(path);
        if (!Files.exists(file)) return null;
        try {
            State state = JSONUtil.toBean(Files.readString(file), State.class);
            if (active(state.getStatus()) && !instanceId.equals(state.getInstanceId())) {
                state.setStatus("failed");
                state.setMessage("服务已重启，上一轮生成或构建被中断，请重新生成");
                state.setFinishedAt(System.currentTimeMillis());
                write(path, state);
            }
            return state;
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取构建状态失败");
        }
    }

    private void write(String path, State state) {
        Path file = statusFile(path);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), ".build-status-", ".tmp");
            try {
                Files.writeString(temp, JSONUtil.toJsonStr(state));
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存构建状态失败");
        }
    }
}
