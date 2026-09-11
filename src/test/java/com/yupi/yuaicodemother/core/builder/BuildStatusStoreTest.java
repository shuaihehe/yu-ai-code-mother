package com.yupi.yuaicodemother.core.builder;

import com.yupi.yuaicodemother.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildStatusStoreTest {
    @TempDir Path root;
    private final BuildStatusStore store = new BuildStatusStore();

    private String project() { return root.resolve("vue_project_1").toString(); }

    @Test
    void oldDistDoesNotHideRunningOrFailedBuild() throws Exception {
        Path dist = Files.createDirectories(Path.of(project(), "dist"));
        Files.writeString(dist.resolve("index.html"), "old page");
        String id = store.start(project(), "building");
        assertEquals("building", store.getStatus(project()).getStatus());
        assertTrue(store.getStatus(project()).isBuilding());
        assertTrue(store.getStatus(project()).isDistExists());
        store.transition(project(), id, "building", "failed", "打包失败");
        assertEquals("failed", store.getStatus(project()).getStatus());
        assertEquals("打包失败", store.getStatus(project()).getMessage());
        assertFalse(store.getStatus(project()).isBuilding());
    }

    @Test
    void refusesOverlappingGenerationAndRejectsStaleCompletion() {
        String first = store.start(project(), "generating");
        assertThrows(BusinessException.class, () -> store.start(project(), "building"));
        store.transition(project(), first, "generating", "failed", "中断");
        String second = store.start(project(), "generating");
        assertNotEquals(first, second);
        assertFalse(store.transition(project(), first, "generating", "completed", "旧任务完成"));
        assertEquals(second, store.getStatus(project()).getBuildId());
        assertEquals("generating", store.getStatus(project()).getStatus());
    }

    @Test
    void restartPreservesSuccessAndMarksInterruptedBuildFailed() {
        String id = store.start(project(), "building");
        store.transition(project(), id, "building", "completed", "完成");
        BuildStatusStore restarted = new BuildStatusStore();
        assertEquals("completed", restarted.getStatus(project()).getStatus());
        assertNotNull(restarted.getStatus(project()).getBuildTime());
        restarted.start(project(), "building");
        assertEquals("failed", new BuildStatusStore().getStatus(project()).getStatus());
    }

    @Test
    void historicalFilesAreNotReportedAsTrackedSuccess() throws Exception {
        assertEquals("not_found", store.getStatus(project()).getStatus());
        Files.createDirectories(Path.of(project(), "dist"));
        assertEquals("pending", store.getStatus(project()).getStatus());
        Files.writeString(Path.of(project(), "dist/index.html"), "old page");
        assertEquals("ready", store.getStatus(project()).getStatus());
        assertNull(store.getStatus(project()).getBuildId());
    }
}
