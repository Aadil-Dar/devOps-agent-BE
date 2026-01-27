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

    @Value("${github.username:#{null}}")
    private String githubUsername;

    @Value("${github.password:#{null}}")
    private String githubPassword;

    @Bean
    public GitHub gitHubClient() {
        try {
            // Priority 1: OAuth token (most secure)
            if (githubToken != null && !githubToken.isEmpty()) {
                log.info("Initializing GitHub client with OAuth token");
                return new GitHubBuilder().withOAuthToken(githubToken).build();
            }
            // Priority 2: Username and password/token (basic auth)
            else if (githubUsername != null && !githubUsername.isEmpty()
                    && githubPassword != null && !githubPassword.isEmpty()) {
                log.info("Initializing GitHub client with username and password");
                return new GitHubBuilder()
                        .withPassword(githubUsername, githubPassword)
                        .build();
            }
            // Priority 3: Anonymous (limited rate)
            else {
                log.warn("Initializing GitHub client without authentication (anonymous access) - this has strict rate limits");
                return GitHub.connectAnonymously();
            }
        } catch (IOException e) {
            log.error("Failed to initialize GitHub client", e);
            throw new RuntimeException("Failed to initialize GitHub client", e);
        }
    }
}
