package com.yupi.yuaicodemother.controller;

import com.yupi.yuaicodemother.core.builder.BuildStatusStore;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.model.entity.App;
import com.yupi.yuaicodemother.model.entity.User;
import com.yupi.yuaicodemother.model.vo.BuildStatusVO;
import com.yupi.yuaicodemother.service.AppService;
import com.yupi.yuaicodemother.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BuildStatusControllerTest {
    private final AppController controller = new AppController();
    private final AppService apps = mock(AppService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    private final UserService users = mock(UserService.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    private final BuildStatusStore statuses = mock(BuildStatusStore.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final User user = new User();
    private final App app = new App();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(controller, "appService", apps);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "buildStatusStore", statuses);
        user.setId(1L);
        user.setUserRole("user");
        app.setId(2L);
        app.setUserId(1L);
        app.setCodeGenType("vue_project");
        when(users.getLoginUser(request)).thenReturn(user);
        when(apps.getById(2L)).thenReturn(app);
        when(statuses.getStatus(anyString())).thenReturn(new BuildStatusVO());
    }

    @Test void ownerAndAdminCanQuery() {
        assertEquals(2L, controller.getBuildStatus(2L, request).getData().getAppId());
        user.setId(3L);
        user.setUserRole("admin");
        assertEquals(0, controller.getBuildStatus(2L, request).getCode());
    }

    @Test void endpointSerializesStatusAndBuildingFlag() throws Exception {
        when(users.getLoginUser(any())).thenReturn(user);
        BuildStatusVO status = new BuildStatusVO();
        status.setStatus("building");
        status.setBuilding(true);
        when(statuses.getStatus(anyString())).thenReturn(status);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/app/build/status/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("building"))
                .andExpect(jsonPath("$.data.isBuilding").value(true))
                .andExpect(jsonPath("$.data.building").doesNotExist());
    }

    @Test void unrelatedUserCannotReadStatus() {
        user.setId(3L);
        assertThrows(BusinessException.class, () -> controller.getBuildStatus(2L, request));
        verifyNoInteractions(statuses);
    }

    @Test void validatesIdAppAndProjectType() {
        assertThrows(BusinessException.class, () -> controller.getBuildStatus(0L, request));
        assertThrows(BusinessException.class, () -> controller.getBuildStatus(99L, request));
        app.setCodeGenType("html");
        assertThrows(BusinessException.class, () -> controller.getBuildStatus(2L, request));
        verifyNoInteractions(statuses);
    }
}
