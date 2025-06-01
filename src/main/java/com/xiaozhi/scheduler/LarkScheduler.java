package com.xiaozhi.scheduler;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.Resource;
import reactor.core.publisher.Mono;

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
    @Scheduled(cron = "0 0 */3 * * ?")
    public void callingCommunityHospitalLarkBot() {
        // 当前时间是否处于早上9点到晚上8点
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(20, 0);
        if (now.isBefore(start) || now.isAfter(end)) {
            logger.info("The current time is not between 9 AM and 8 PM, so the task will not be executed.");
            return;
        }
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

    @Scheduled(cron = "0 0 */3 * * ?")
    public void callingOldManServerLarkBot() {
        // 当前时间是否处于早上9点到晚上8点
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(20, 0);
        if (now.isBefore(start) || now.isAfter(end)) {
            logger.info("The current time is not between 9 AM and 8 PM, so the task will not be executed.");
            return;
        }
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