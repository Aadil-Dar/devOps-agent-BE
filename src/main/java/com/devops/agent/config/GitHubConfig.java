package com.devops.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class GitHubConfig {

    @Value("${github.token:#{null}}")
    private String githubToken;

    @Value("${github.repository.owner:sdc-pune}")
    private String repositoryOwner;

    @Value("${github.repository.name:devOps-agent}")
    private String repositoryName;

    @Bean
    public GitHub gitHubClient() {
        try {
            if (githubToken != null && !githubToken.isEmpty()) {
                log.info("Initializing GitHub client with authentication token");
                return new GitHubBuilder().withOAuthToken(githubToken).build();
            } else {
                log.info("Initializing GitHub client without authentication (anonymous access)");
                return GitHub.connectAnonymously();
            }
        } catch (IOException e) {
            log.error("Failed to initialize GitHub client", e);
            throw new RuntimeException("Failed to initialize GitHub client", e);
        }
    }

    @Bean
    public String repositoryOwner() {
        return repositoryOwner;
    }

    @Bean
    public String repositoryName() {
        return repositoryName;
    }
}
