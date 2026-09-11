package com.yupi.yuaicodemother.service.impl;

import com.yupi.yuaicodemother.core.AiCodeGeneratorFacade;
import com.yupi.yuaicodemother.core.builder.BuildStatusStore;
import com.yupi.yuaicodemother.core.builder.VueProjectBuilder;
import com.yupi.yuaicodemother.core.handler.StreamHandlerExecutor;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.model.entity.App;
import com.yupi.yuaicodemother.model.entity.User;
import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.yupi.yuaicodemother.model.vo.BuildStatusVO;
import com.yupi.yuaicodemother.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VueGenerationLifecycleTest {
    @TempDir Path root;
    private AppServiceImpl service;
    private BuildStatusStore store;
    private AiCodeGeneratorFacade facade;
    private VueProjectBuilder builder;
    private final User user = new User();
    private final Sinks.Many<String> source = Sinks.many().unicast().onBackpressureBuffer();

    private <T> T stub(Class<T> type) {
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    }

    @BeforeEach void setup() {
        String path = root.resolve("vue_project_1").toString();
        store = new BuildStatusStore() {
            @Override public synchronized String start(String ignored, String status) { return super.start(path, status); }
            @Override public synchronized boolean transition(String ignored, String id, String expected, String status, String message) {
                return super.transition(path, id, expected, status, message);
            }
            @Override public synchronized BuildStatusVO getStatus(String ignored) { return super.getStatus(path); }
        };
        service = mock(AppServiceImpl.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS).defaultAnswer(CALLS_REAL_METHODS));
        App app = new App();
        app.setId(1L);
        app.setUserId(2L);
        app.setCodeGenType("vue_project");
        user.setId(2L);
        doReturn(app).when(service).getById(1L);
        facade = stub(AiCodeGeneratorFacade.class);
        builder = stub(VueProjectBuilder.class);
        StreamHandlerExecutor handler = stub(StreamHandlerExecutor.class);
        when(facade.generateAndSaveCodeStream(anyString(), eq(CodeGenTypeEnum.VUE_PROJECT), eq(1L))).thenReturn(source.asFlux());
        when(handler.doExecute(any(), any(), eq(1L), eq(user), eq(CodeGenTypeEnum.VUE_PROJECT))).thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> {
            assertTrue(store.transition(call.getArgument(0), call.getArgument(1), "generating", "building", "构建中"));
            return null;
        }).when(builder).buildGeneratedProjectAsync(anyString(), anyString());
        ReflectionTestUtils.setField(service, "buildStatusStore", store);
        ReflectionTestUtils.setField(service, "vueProjectBuilder", builder);
        ReflectionTestUtils.setField(service, "aiCodeGeneratorFacade", facade);
        ReflectionTestUtils.setField(service, "streamHandlerExecutor", handler);
        ReflectionTestUtils.setField(service, "chatHistoryService", stub(ChatHistoryService.class));
    }

    @Test void buildingIsRecordedBeforeResponseCompletes() {
        AtomicReference<String> stateAtCompletion = new AtomicReference<>();
        service.chatToGenCode(1L, "生成网站", user).subscribe(value -> {}, error -> fail(error),
                () -> stateAtCompletion.set(store.getStatus("").getStatus()));
        assertEquals("generating", store.getStatus("").getStatus());
        source.tryEmitNext("代码");
        source.tryEmitComplete();
        assertEquals("building", stateAtCompletion.get());
        verify(builder).buildGeneratedProjectAsync(anyString(), anyString());
    }

    @Test void browserDisconnectKeepsTaskAliveAndBlocksSecondGeneration() {
        Disposable connection = service.chatToGenCode(1L, "生成网站", user).subscribe();
        connection.dispose();
        assertEquals("generating", store.getStatus("").getStatus());
        assertThrows(BusinessException.class, () -> service.chatToGenCode(1L, "再次生成", user).blockLast());
        source.tryEmitComplete();
        assertEquals("building", store.getStatus("").getStatus());
    }

    @Test void generationErrorRecordsFailureWithoutStartingBuild() {
        when(facade.generateAndSaveCodeStream(anyString(), any(), anyLong())).thenReturn(Flux.error(new IllegalStateException("AI unavailable")));
        assertThrows(IllegalStateException.class, () -> service.chatToGenCode(1L, "生成网站", user).blockLast());
        assertEquals("failed", store.getStatus("").getStatus());
        verifyNoInteractions(builder);
    }
}
