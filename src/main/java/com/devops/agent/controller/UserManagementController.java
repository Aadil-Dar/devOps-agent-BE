package com.devops.agent.controller;

import com.devops.agent.model.MessageResponse;
import com.devops.agent.model.User;
import com.devops.agent.service.ProjectConfigurationService;
import com.devops.agent.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserManagementController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProjectConfigurationService projectConfigurationService;

    /**
     * Get all users (Admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userService.findAll();

            // Remove passwords from response
            List<Map<String, Object>> sanitizedUsers = users.stream()
                    .map(this::sanitizeUser)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(sanitizedUsers);
        } catch (Exception e) {
            log.error("Error fetching users: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error fetching users: " + e.getMessage()));
        }
    }

    /**
     * Get user by ID (Admin only)
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(sanitizeUser(user));
        } catch (Exception e) {
            log.error("Error fetching user: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error fetching user: " + e.getMessage()));
        }
    }

    /**
     * Assign project to user and optionally set DevOps access (Admin only)
     */
    @PostMapping("/{userId}/assign-project")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assignProjectToUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        try {
            String projectId = (String) request.get("projectId");
            Boolean enableToUseDevops = (Boolean) request.getOrDefault("enableToUseDevops", false);

            if (projectId == null || projectId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Project ID is required"));
            }

            // Verify project exists
            if (!projectConfigurationService.projectExists(projectId)) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Project not found: " + projectId));
            }

            // Get user and update both projectId and enableToUseDevops
            User user = userService.findById(userId);
            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("User not found"));
            }

            user.setProjectId(projectId);
            user.setEnableToUseDevops(enableToUseDevops);
            User updatedUser = userService.updateUser(user);

            return ResponseEntity.ok(sanitizeUser(updatedUser));
        } catch (Exception e) {
            log.error("Error assigning project: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error assigning project: " + e.getMessage()));
        }
    }

    /**
     * Remove project from user (Admin only)
     */
    @PostMapping("/{userId}/remove-project")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removeProjectFromUser(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            user.setProjectId(null);
            User updatedUser = userService.updateUser(user);

            return ResponseEntity.ok(sanitizeUser(updatedUser));
        } catch (Exception e) {
            log.error("Error removing project: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error removing project: " + e.getMessage()));
        }
    }

    /**
     * Enable/Disable DevOps access for user (Admin only)
     */
    @PostMapping("/{userId}/toggle-access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleDevOpsAccess(
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> request) {
        try {
            Boolean enable = request.get("enable");

            if (enable == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("'enable' flag is required"));
            }

            User updatedUser = userService.toggleDevopsAccess(userId, enable);

            return ResponseEntity.ok(sanitizeUser(updatedUser));
        } catch (Exception e) {
            log.error("Error toggling access: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error toggling access: " + e.getMessage()));
        }
    }

    /**
     * Update user details (Admin only)
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> updates) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Update allowed fields
            if (updates.containsKey("email")) {
                user.setEmail((String) updates.get("email"));
            }
            if (updates.containsKey("projectId")) {
                user.setProjectId((String) updates.get("projectId"));
            }
            if (updates.containsKey("enableToUseDevops")) {
                user.setEnableToUseDevops((Boolean) updates.get("enableToUseDevops"));
            }
            if (updates.containsKey("enabled")) {
                user.setEnabled((Boolean) updates.get("enabled"));
            }
            if (updates.containsKey("roles")) {
                user.setRoles((List<String>) updates.get("roles"));
            }

            User updatedUser = userService.updateUser(user);

            return ResponseEntity.ok(sanitizeUser(updatedUser));
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error updating user: " + e.getMessage()));
        }
    }

    /**
     * Delete user (Admin only)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Prevent deleting admin user
            if (user.getRoles().contains("ADMIN")) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Cannot delete admin user"));
            }

            userService.deleteByUsername(user.getUsername());

            return ResponseEntity.ok(new MessageResponse("User deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error deleting user: " + e.getMessage()));
        }
    }

    /**
     * Get users by project (Admin only)
     */
    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUsersByProject(@PathVariable String projectId) {
        try {
            List<User> users = userService.findUsersByProjectId(projectId);

            List<Map<String, Object>> sanitizedUsers = users.stream()
                    .map(this::sanitizeUser)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(sanitizedUsers);
        } catch (Exception e) {
            log.error("Error fetching users by project: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Error fetching users: " + e.getMessage()));
        }
    }

    /**
     * Helper method to remove sensitive data from user object
     */
    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("id", user.getId());
        sanitized.put("username", user.getUsername());
        sanitized.put("email", user.getEmail());
        sanitized.put("roles", user.getRoles());
        sanitized.put("projectId", user.getProjectId());

        // Fetch and include projectName if user has a projectId
        String projectName = null;
        if (user.getProjectId() != null && !user.getProjectId().isEmpty()) {
            try {
                projectName = projectConfigurationService.getConfiguration(user.getProjectId())
                        .map(config -> config.getProjectName())
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Failed to fetch project name for projectId: {}", user.getProjectId(), e);
            }
        }
        sanitized.put("projectName", projectName);

        sanitized.put("enableToUseDevops", user.getEnableToUseDevops());
        sanitized.put("enabled", user.isEnabled());
        sanitized.put("createdAt", user.getCreatedAt());
        sanitized.put("updatedAt", user.getUpdatedAt());
        return sanitized;
    }
}

