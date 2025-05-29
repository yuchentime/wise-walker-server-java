package com.xiaozhi.dao;

import com.xiaozhi.entity.CozeChatHistory;

import java.util.List;
import java.util.Map;

public interface CozeChatHistoryMapper {

    void insert(CozeChatHistory record);

    /**
     * 根据条件查询聊天历史记录
     * @param params 包含查询条件的参数Map，支持createdAtStart、createdAtEnd和dataSource
     * @return 符合条件的聊天历史记录列表
     */
    List<CozeChatHistory> selectByConditions(Map<String, Object> params);
}