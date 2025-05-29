package com.xiaozhi.scheduler;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LarkScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LarkScheduler.class);

    // 社区医院服务数据采集-消息推送
    private final String larkWebhookUrl = "https://www.feishu.cn/flow/api/trigger-webhook/24a9536f4777f2317da3b0fd8ff0a7ab";

    /**
     * 定时任务：每3小时（早上9点 ～ 晚上8点）调用飞书webhook通知
     */
    @Scheduled(cron = "0 9-20/3 * * * ?")
    public void callingLarkBot() {
        // 1. 创建OkHttpClient实例
        OkHttpClient client = new OkHttpClient();

        // 2. 准备JSON请求体
        String json = "{}";
        RequestBody requestBody = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );

        // 3. 创建Request对象
        Request request = new Request.Builder()
                .url(larkWebhookUrl)  // 替换为你的API地址
                .post(requestBody)
                .build();
        try {
            Response response = client.newCall(request).execute();
            logger.info("调用飞书webhook通知成功: {}", response.body().string());
        } catch (IOException e) {
            logger.error("调用飞书webhook通知失败", e);
        }
    }
}