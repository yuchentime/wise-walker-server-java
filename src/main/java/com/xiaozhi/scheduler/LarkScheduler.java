package com.xiaozhi.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.Resource;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;

@Component
public class LarkScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LarkScheduler.class);

    // 社区医院服务数据采集-消息推送
    private final String COMMUNITY_HOSPITAL_MESSAGE_WEBHOOK = "https://www.feishu.cn/flow/api/trigger-webhook/24a9536f4777f2317da3b0fd8ff0a7ab";

    // 老年服务数据采集-消息推送
    private final String OLD_MAN_SERVER_MESSAGE_WEBHOOK = "https://www.feishu.cn/flow/api/trigger-webhook/037f63c9feade5cec57ff62e5d502036";

    @Resource
    private WebClient webClient;

    /**
     * 定时任务：每3小时（早上9点 ～ 晚上8点）调用飞书webhook通知
     */
    @Scheduled(cron = "0 8-21/3 * * * ?")
    public void callingCommunityHospitalLarkBot() {
        webClient.post().uri(COMMUNITY_HOSPITAL_MESSAGE_WEBHOOK)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .retrieve()
            .onStatus(httpStatus -> httpStatus.is4xxClientError(), response -> {
                logger.error("调用【老年服务数据采集-消息推送】异常: {}", response.statusCode());
                return Mono.error(new RuntimeException("调用【社区医院服务数据采集-消息推送】异常: Client error"));
            })
            .onStatus(httpStatus -> httpStatus.is5xxServerError(), response -> {
                logger.error("调用【老年服务数据采集-消息推送】异常: {}", response.statusCode());
                return Mono.error(new RuntimeException("调用【社区医院服务数据采集-消息推送】异常: Server error"));
            })
            .bodyToMono(String.class)
            .subscribe(result -> {
                logger.info("调用【社区医院服务数据采集-消息推送】飞书webhook通知成功: {}", result);
            });
    }

    @Scheduled(cron = "0 8-21/3 * * * ?")
    public void callingOldManServerLarkBot() {
        webClient.post().uri(OLD_MAN_SERVER_MESSAGE_WEBHOOK)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .retrieve()
            .onStatus(httpStatus -> httpStatus.is4xxClientError(), response -> {
                logger.error("调用【老年服务数据采集-消息推送】异常: {}", response.statusCode());
                return Mono.error(new RuntimeException("调用【老年服务数据采集-消息推送】异常: Client error"));
            })
            .onStatus(httpStatus -> httpStatus.is5xxServerError(), response -> {
                logger.error("调用【老年服务数据采集-消息推送】异常: {}", response.statusCode());
                return Mono.error(new RuntimeException("调用【老年服务数据采集-消息推送】异常: Server error"));
            })
            .bodyToMono(String.class)
            .subscribe(result -> {
                logger.info("调用【老年服务数据采集-消息推送】飞书webhook通知成功: {}", result);
            });
    }
}