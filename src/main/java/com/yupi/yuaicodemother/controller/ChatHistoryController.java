package com.yupi.yuaicodemother.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.yuaicodemother.annotation.AuthCheck;
import com.yupi.yuaicodemother.common.BaseResponse;
import com.yupi.yuaicodemother.common.ResultUtils;
import com.yupi.yuaicodemother.constant.UserConstant;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.exception.ThrowUtils;
import com.yupi.yuaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yupi.yuaicodemother.model.entity.App;
import com.yupi.yuaicodemother.model.entity.User;
import com.yupi.yuaicodemother.service.AppService;
import com.yupi.yuaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;
import com.yupi.yuaicodemother.model.entity.ChatHistory;
import com.yupi.yuaicodemother.service.ChatHistoryService;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    /**
     * 应用对话历史单次最多加载数量
     */
    private static final int MAX_APP_HISTORY_PAGE_SIZE = 20;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private AppService appService;

    @Autowired
    private UserService userService;

    /**
     * 游标分页查询某个应用的对话历史，仅应用创建者和管理员可见。
     * 返回结果按时间倒序排列，前端展示时需要反转为正序。
     *
     * @param appId          应用 id
     * @param pageSize       单次加载数量，默认 10 条
     * @param lastCreateTime 上一页最早一条消息的创建时间
     * @param request        HTTP 请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastCreateTime,
            HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > MAX_APP_HISTORY_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR, "每次最多加载 20 条对话历史");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isOwner = Objects.equals(app.getUserId(), loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isOwner, ErrorCode.NO_AUTH_ERROR,
                "仅应用创建者和管理员可以查看对话历史");
        Page<ChatHistory> chatHistoryPage = chatHistoryService.listAppChatHistory(
                appId, pageSize, lastCreateTime);
        return ResultUtils.success(chatHistoryPage);
    }

    /**
     * 管理员分页查询全部应用的对话历史，按消息时间倒序排列。
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(
            @RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0 || pageSize <= 0,
                ErrorCode.PARAMS_ERROR, "分页参数错误");
        Page<ChatHistory> chatHistoryPage = chatHistoryService.page(
                Page.of(pageNum, pageSize),
                chatHistoryService.getQueryWrapper(chatHistoryQueryRequest));
        return ResultUtils.success(chatHistoryPage);
    }

}
