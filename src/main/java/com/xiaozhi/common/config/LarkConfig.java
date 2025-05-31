package com.xiaozhi.common.config;

import com.lark.oapi.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LarkConfig {

    @Value("${lark.appId:}")
    private String appId;
    @Value("${lark.appSecret:}")
    private String appSecret;

    @Bean
    public Client larkClient() {
        Client client = Client.newBuilder(appId, appSecret).build();
        return client;
    }

}
