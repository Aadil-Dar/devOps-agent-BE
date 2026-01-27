package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User implements UserDetails {

    private String id;
    private String username;
    private String password;
    private String email;
    private List<String> roles;
    private String projectId;
    private Boolean enableToUseDevops;
    private Long createdAt;
    private Long updatedAt;
    private boolean enabled;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    @DynamoDbAttribute("username")
    public String getUsername() {
        return username;
    }

    @Override
    @DynamoDbAttribute("password")
    public String getPassword() {
        return password;
    }

    @DynamoDbAttribute("email")
    public String getEmail() {
        return email;
    }

    @DynamoDbAttribute("roles")
    public List<String> getRoles() {
        return roles;
    }

    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbAttribute("enableToUseDevops")
    public Boolean getEnableToUseDevops() {
        return enableToUseDevops;
    }

    @DynamoDbAttribute("createdAt")
    public Long getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Long getUpdatedAt() {
        return updatedAt;
    }

    @DynamoDbAttribute("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @DynamoDbAttribute("accountNonExpired")
    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @DynamoDbAttribute("accountNonLocked")
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @DynamoDbAttribute("credentialsNonExpired")
    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }
}

