package com.yupi.yuaicodemother.service;

import com.mybatisflex.core.service.IService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.yuaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yupi.yuaicodemother.model.entity.ChatHistory;
import com.yupi.yuaicodemother.model.enums.ChatHistoryMessageTypeEnum;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存一条对话消息
     *
     * @param appId           应用 id
     * @param userId          用户 id
     * @param message         消息内容
     * @param messageTypeEnum 消息类型
     */
    void addChatMessage(Long appId, Long userId, String message,
                        ChatHistoryMessageTypeEnum messageTypeEnum);

    /**
     * 游标分页查询某个应用的对话历史
     *
     * @param appId         应用 id
     * @param pageSize      查询数量
     * @param lastCreateTime 创建时间游标，为空时从最新消息开始查询
     * @return 对话历史分页
     */
    Page<ChatHistory> listAppChatHistory(Long appId, int pageSize, LocalDateTime lastCreateTime);

    /**
     * 根据查询条件构造查询参数
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 删除某个应用的全部对话历史
     *
     * @param appId 应用 id
     * @return 是否执行成功
     */
    boolean removeByAppId(Long appId);
}
