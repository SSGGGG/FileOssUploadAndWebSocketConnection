package com.excelman.fileossuploadandwebsocketconnection.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

/**
 * @author Excelman
 * @date 2021/9/28 上午8:43
 * @description
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
    /**
     * 注册Stomp协议的节点（endpoint），并指定映射的url
     * @param registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/endpointExcelman").setAllowedOrigins("*").withSockJS();
    }
    /**
     * 配置消息代理
     * @param registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 点对点配置一个消息代理
        registry.enableSimpleBroker("/uuid");
        // 点对点使用的订阅前缀
        registry.setUserDestinationPrefix("/uuid");
    }
}
