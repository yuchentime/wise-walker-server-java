package com.xiaozhi.websocket.llm.providers;

import com.coze.openapi.client.chat.CreateChatReq;
import com.coze.openapi.client.chat.model.ChatEvent;
import com.coze.openapi.client.chat.model.ChatEventType;
import com.coze.openapi.client.chat.model.ChatToolCall;
import com.coze.openapi.client.connversations.message.model.Message;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.websocket.llm.api.AbstractLlmService;
import com.xiaozhi.websocket.llm.api.StreamResponseListener;
import com.xiaozhi.websocket.llm.memory.ModelContext;
import com.xiaozhi.websocket.llm.tool.ActionType;
import com.xiaozhi.websocket.llm.tool.ToolResponse;
import io.github.imfangs.dify.client.DifyChatClient;
import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.callback.ChatStreamCallback;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.event.*;
import io.github.imfangs.dify.client.model.chat.ChatMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DifyService extends AbstractLlmService {
    DifyChatClient chatClient;

    /**
     * 构造函数
     *
     * @param endpoint  API端点
     * @param appId
     * @param apiKey    API密钥
     * @param apiSecret
     * @param model     模型名称
     */
    public DifyService(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        super(endpoint, appId, apiKey, apiSecret, model);

        chatClient = DifyClientFactory.createChatClient(endpoint, apiKey);

        logger.info("初始化Dify服务 ", endpoint, apiKey);
    }

    @Override
    public String chat(List<Map<String, Object>> messages) throws IOException {
        return "";
    }

    @Override
    protected void chatStream(List<Map<String, Object>> messages, StreamResponseListener streamListener, ModelContext modelContext) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IOException("消息列表不能为空");
        }

        // 通知开始
        streamListener.onStart();

        // 创建唯一的用户ID
        String userId = "user_xz_" + modelContext.getDeviceId().replace(":", "");
        Map<String, Object> map = messages.get(messages.size() - 1);
        ChatMessage message = ChatMessage.builder()
                .user(userId)
                .query(map.get("content").toString())
                .responseMode(ResponseMode.STREAMING)
                .build();

        StringBuilder fullResponse = new StringBuilder();
// 发送流式消息
        chatClient.sendChatMessageStream(message, new ChatStreamCallback() {
            @Override
            public void onMessage(MessageEvent event) {
                logger.info("收到消息片段: {}", event.getAnswer());
            }

            @Override
            public void onAgentMessage(AgentMessageEvent event) {
                logger.info("onAgentMessage: {}", event.getAnswer());
                fullResponse.append(event.getAnswer());
                streamListener.onToken(event.getAnswer());
            }

            @Override
            public void onMessageReplace(MessageReplaceEvent event) {
                logger.info("onMessageReplace: {}", event.getAnswer());
            }

            @Override
            public void onTTSMessage(TtsMessageEvent event) {
                logger.info("onTTSMessage");
            }

            @Override
            public void onMessageEnd(MessageEndEvent event) {
                logger.info("消息结束，完整消息ID: {}", event.getMessageId());
                // 通知完成
                streamListener.onComplete(fullResponse.toString());
                streamListener.onFinal(messages, DifyService.this);
            }

            @Override
            public void onError(ErrorEvent event) {
                logger.error("错误: {}", event.getMessage());
            }

            @Override
            public void onException(Throwable throwable) {
                logger.error("异常: {}", throwable.getMessage());
                streamListener.onError(new IOException(throwable));
            }
        });
    }

    @Override
    public String getProviderName() {
        return "dify";
    }
}
