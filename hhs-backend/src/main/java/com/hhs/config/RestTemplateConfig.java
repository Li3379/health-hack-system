package com.hhs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate configuration for external API calls
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Primary RestTemplate bean for general HTTP calls including OAuth.
     * Configured with connection and read timeouts.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10 seconds connect timeout
        factory.setReadTimeout(30000);     // 30 seconds read timeout
        return new RestTemplate(factory);
    }

    /**
     * RestTemplate bean for push channel HTTP calls
     * Configured with shorter timeouts for push notifications
     */
    @Bean
    public RestTemplate pushRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 seconds connect timeout
        factory.setReadTimeout(10000);     // 10 seconds read timeout
        return new RestTemplate(factory);
    }
}