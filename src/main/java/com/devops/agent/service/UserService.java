package com.devops.agent.service;

import com.devops.agent.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private DynamoDbTable<User> userTable;

    private final PasswordEncoder passwordEncoder;

    public UserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        // Create default admin user if not exists
        if (!existsByUsername("admin")) {
            User admin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@devops.com")
                    .roles(Arrays.asList("ADMIN", "USER"))
                    .enableToUseDevops(true)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .createdAt(System.currentTimeMillis())
                    .updatedAt(System.currentTimeMillis())
                    .build();
            saveUserToDynamoDB(admin);
            log.info("Default admin user created");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        return user;
    }

    public UserDetails loadUserByEmail(String email) throws UsernameNotFoundException {
        User user = findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        return user;
    }

    public User findByUsername(String username) {
        try {
            // Scan to find user by username (secondary attribute)
            List<User> users = new ArrayList<>();
            userTable.scan().items().forEach(users::add);

            return users.stream()
                    .filter(user -> user.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);
        } catch (DynamoDbException e) {
            log.error("Failed to find user by username: {}", e.getMessage(), e);
            return null;
        }
    }

    public User findByEmail(String email) {
        try {
            // Scan to find user by email (secondary attribute)
            List<User> users = new ArrayList<>();
            userTable.scan().items().forEach(users::add);

            return users.stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);
        } catch (DynamoDbException e) {
            log.error("Failed to find user by email: {}", e.getMessage(), e);
            return null;
        }
    }

    public User findById(String id) {
        try {
            Key key = Key.builder().partitionValue(id).build();
            return userTable.getItem(key);
        } catch (DynamoDbException e) {
            log.error("Failed to find user by id: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    public boolean existsByEmail(String email) {
        try {
            List<User> users = new ArrayList<>();
            userTable.scan().items().forEach(users::add);

            return users.stream()
                    .anyMatch(user -> user.getEmail().equals(email));
        } catch (DynamoDbException e) {
            log.error("Failed to check email existence: {}", e.getMessage(), e);
            return false;
        }
    }

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }

        // Hash password if it's not already hashed
        if (!user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // Set default values
        if (user.getEnableToUseDevops() == null) {
            user.setEnableToUseDevops(false);
        }

        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        if (user.getCreatedAt() == null) {
            user.setCreatedAt(System.currentTimeMillis());
        }
        user.setUpdatedAt(System.currentTimeMillis());

        return saveUserToDynamoDB(user);
    }

    private User saveUserToDynamoDB(User user) {
        try {
            userTable.putItem(user);
            log.info("Successfully saved user: {}", user.getUsername());
            return user;
        } catch (DynamoDbException e) {
            log.error("Failed to save user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    public User updateUser(User user) {
        user.setUpdatedAt(System.currentTimeMillis());
        return saveUserToDynamoDB(user);
    }

    public List<User> findAll() {
        try {
            List<User> users = new ArrayList<>();
            userTable.scan().items().forEach(users::add);
            log.info("Found {} users", users.size());
            return users;
        } catch (DynamoDbException e) {
            log.error("Failed to list users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list users", e);
        }
    }

    public void deleteByUsername(String username) {
        User user = findByUsername(username);
        if (user != null) {
            try {
                Key key = Key.builder().partitionValue(user.getId()).build();
                userTable.deleteItem(key);
                log.info("Successfully deleted user: {}", username);
            } catch (DynamoDbException e) {
                log.error("Failed to delete user: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to delete user", e);
            }
        }
    }

    public User assignProjectToUser(String userId, String projectId) {
        User user = findById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        user.setProjectId(projectId);
        user.setEnableToUseDevops(true);
        return updateUser(user);
    }

    public User toggleDevopsAccess(String userId, boolean enable) {
        User user = findById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        user.setEnableToUseDevops(enable);
        return updateUser(user);
    }

    public List<User> findUsersByProjectId(String projectId) {
        try {
            List<User> users = new ArrayList<>();
            userTable.scan().items().forEach(users::add);

            return users.stream()
                    .filter(user -> projectId.equals(user.getProjectId()))
                    .toList();
        } catch (DynamoDbException e) {
            log.error("Failed to find users by project: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find users by project", e);
        }
    }
}

