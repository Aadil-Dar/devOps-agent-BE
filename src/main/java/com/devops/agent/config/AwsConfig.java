package com.devops.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Bean
    public CodePipelineClient codePipelineClient() {
        return CodePipelineClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public CloudWatchLogsClient cloudWatchLogsClient() {
        return CloudWatchLogsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SecurityHubClient securityHubClient() {
        return SecurityHubClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public Inspector2Client inspector2Client() {
        return Inspector2Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
