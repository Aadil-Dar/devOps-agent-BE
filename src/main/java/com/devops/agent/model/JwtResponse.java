package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String token;
    private String type = "Bearer";
    private String id;
    private String username;
    private String email;
    private List<String> roles;
    private String projectId;
    private Boolean enableToUseDevops;

    public JwtResponse(String token, String id, String username, String email, List<String> roles, String projectId, Boolean enableToUseDevops) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.projectId = projectId;
        this.enableToUseDevops = enableToUseDevops;
    }
}

