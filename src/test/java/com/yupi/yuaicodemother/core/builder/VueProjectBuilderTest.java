package com.yupi.yuaicodemother.core.builder;

import com.yupi.yuaicodemother.constant.AppConstant;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

/**
 * VueProjectBuilder 构建功能测试
 * 注意：buildProjectSuccess 会真实执行 npm install 与 npm run build，耗时较长
 */
@SpringBootTest
class VueProjectBuilderTest {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 真实构建已存在的 Vue 项目（vue_project_2，已含 node_modules）
     * 验证：构建成功返回 true，dist/index.html 生成
     */
    @Test
    void buildProjectSuccess() {
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_2";
        boolean result = vueProjectBuilder.buildProject(projectPath);
        Assertions.assertTrue(result, "构建 Vue 项目应成功");
        File distIndex = new File(projectPath, "dist/index.html");
        Assertions.assertTrue(distIndex.exists(), "构建后应生成 dist/index.html");
    }

    /**
     * 项目目录不存在时应返回 false，而不是抛异常
     */
    @Test
    void buildProjectDirNotExist() {
        boolean result = vueProjectBuilder.buildProject("/tmp/not_exist_vue_project_" + System.currentTimeMillis());
        Assertions.assertFalse(result);
    }

    /**
     * 目录存在但缺少 package.json 时应返回 false
     */
    @Test
    void buildProjectMissingPackageJson() {
        String emptyDirPath = System.getProperty("java.io.tmpdir") + "/empty_vue_project_" + System.currentTimeMillis();
        File emptyDir = new File(emptyDirPath);
        Assertions.assertTrue(emptyDir.mkdirs() || emptyDir.exists(), "准备空目录失败");
        boolean result = vueProjectBuilder.buildProject(emptyDirPath);
        Assertions.assertFalse(result);
    }

    /**
     * 异步构建：调用应立即返回（不阻塞），后台完成后 dist 被刷新
     */
    @Test
    void buildProjectAsync() throws InterruptedException {
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_2";
        File distIndex = new File(projectPath, "dist/index.html");
        long before = distIndex.exists() ? distIndex.lastModified() : 0L;

        long start = System.currentTimeMillis();
        vueProjectBuilder.buildProjectAsync(projectPath);
        long cost = System.currentTimeMillis() - start;
        Assertions.assertTrue(cost < 3000, "异步调用应立即返回，实际耗时 " + cost + "ms");

        // 轮询等待后台构建完成（最多 5 分钟），dist/index.html 的修改时间应被刷新
        long deadline = System.currentTimeMillis() + 300_000L;
        long lastModified = before;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(3000);
            lastModified = distIndex.exists() ? distIndex.lastModified() : 0L;
            if (lastModified > before) {
                return; // 构建完成
            }
        }
        Assertions.fail("异步构建超时未完成，dist/index.html 未刷新（lastModified=" + lastModified + "）");
    }

}
