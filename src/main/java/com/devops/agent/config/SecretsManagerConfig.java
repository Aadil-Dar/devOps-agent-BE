package com.devops.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import java.net.URI;

/**
 * Configuration for AWS Secrets Manager client
 */
@Configuration
public class SecretsManagerConfig {

    @Value("${aws.region:eu-west-1}")
    private String awsRegion;

    @Value("${aws.secrets-manager.endpoint:}")
    private String endpoint;

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        SecretsManagerClientBuilder builder =  SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}

