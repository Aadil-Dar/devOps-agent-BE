package com.devops.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.devops.agent", "com.example.security"})
public class DevOpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevOpsAgentApplication.class, args);
    }
}
