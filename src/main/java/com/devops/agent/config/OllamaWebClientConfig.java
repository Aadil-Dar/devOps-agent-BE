package com.devops.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaWebClientConfig {

    @Bean
    public WebClient ollamaWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)   // 10s to connect
                .responseTimeout(Duration.ofSeconds(60))                // 60s for whole response
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60))         // 60s read timeout
                        .addHandlerLast(new WriteTimeoutHandler(60)));      // 60s write timeout

        return builder
                .baseUrl("http://localhost:11434")                      // Ollama local
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}