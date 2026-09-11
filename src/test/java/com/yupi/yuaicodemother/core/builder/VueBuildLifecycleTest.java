package com.yupi.yuaicodemother.core.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VueBuildLifecycleTest {
    @TempDir Path root;
    private final BuildStatusStore store = new BuildStatusStore();

    private VueProjectBuilder builder(boolean installSucceeds, boolean buildSucceeds, CountDownLatch release) {
        VueProjectBuilder builder = new VueProjectBuilder() {
            @Override boolean executeCommand(File directory, String command, int timeout) {
                if (release != null) {
                    try { if (!release.await(5, TimeUnit.SECONDS)) return false; }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
                }
                return command.equals("npm install") ? installSucceeds : buildSucceeds;
            }
        };
        ReflectionTestUtils.setField(builder, "buildStatusStore", store);
        return builder;
    }

    private String project(boolean oldDist) throws Exception {
        Path project = Files.createDirectories(root.resolve("vue_project_1"));
        Files.writeString(project.resolve("package.json"), "{}");
        if (oldDist) Files.writeString(Files.createDirectories(project.resolve("dist")).resolve("index.html"), "same content");
        return project.toString();
    }

    @Test void installFailureIsReportedEvenWithOldDist() throws Exception {
        String path = project(true);
        assertFalse(builder(false, true, null).buildProject(path));
        assertEquals("failed", store.getStatus(path).getStatus());
        assertTrue(store.getStatus(path).getMessage().contains("依赖安装失败"));
    }

    @Test void buildFailureAndMissingIndexAreReported() throws Exception {
        String path = project(false);
        assertFalse(builder(true, false, null).buildProject(path));
        assertTrue(store.getStatus(path).getMessage().contains("项目打包失败"));
        assertFalse(builder(true, true, null).buildProject(path));
        assertTrue(store.getStatus(path).getMessage().contains("dist/index.html"));
    }

    @Test void sameHtmlStillCountsAsSuccessfulBuild() throws Exception {
        String path = project(true);
        assertTrue(builder(true, true, null).buildProject(path));
        assertEquals("completed", store.getStatus(path).getStatus());
        assertNotNull(store.getStatus(path).getBuildTime());
    }

    @Test void asyncRegistersBuildingBeforeReturningAndKeepsGenerationId() throws Exception {
        String path = project(true);
        String id = store.start(path, "generating");
        CountDownLatch release = new CountDownLatch(1);
        try {
            builder(true, true, release).buildGeneratedProjectAsync(path, id);
            assertEquals("building", store.getStatus(path).getStatus());
            assertEquals(id, store.getStatus(path).getBuildId());
        } finally { release.countDown(); }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (store.getStatus(path).isBuilding() && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals("completed", store.getStatus(path).getStatus());
    }
}
