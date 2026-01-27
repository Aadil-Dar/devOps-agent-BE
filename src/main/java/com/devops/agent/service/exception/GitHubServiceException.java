package com.devops.agent.service.exception;

public class GitHubServiceException extends RuntimeException {
    public GitHubServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    public GitHubServiceException(String message) {
        super(message);
    }
}

