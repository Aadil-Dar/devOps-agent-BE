package com.devops.agent.controller;

import com.devops.agent.model.*;
import com.devops.agent.security.JwtUtil;
import com.devops.agent.service.ProjectConfigurationService;
import com.devops.agent.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProjectConfigurationService projectConfigurationService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Load user by email first
            User user = userService.findByEmail(loginRequest.getEmail());
            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Error: Invalid email or password"));
            }

            // Authenticate using username (Spring Security requirement)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getUsername(), // Use username for authentication
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User userDetails = (User) authentication.getPrincipal();

            // Check if user has admin role
            boolean isAdmin = userDetails.getRoles().contains("ADMIN");

            // If not admin, check if user has project access
            if (!isAdmin) {
                // Check if user has projectId assigned OR enableToUseDevops is true
                if ((userDetails.getProjectId() == null || userDetails.getProjectId().isEmpty())
                    && (userDetails.getEnableToUseDevops() == null || !userDetails.getEnableToUseDevops())) {
                    return ResponseEntity.status(403)
                            .body(new MessageResponse("Access denied. Please contact admin for project access."));
                }
            }

            String jwt = jwtUtil.generateToken(userDetails);

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority().replace("ROLE_", ""))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new JwtResponse(
                    jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    roles,
                    userDetails.getProjectId(),
                    userDetails.getEnableToUseDevops()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Invalid email or password"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userService.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userService.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user
        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(registerRequest.getPassword())
                .email(registerRequest.getEmail())
                .roles(registerRequest.getRoles() != null && !registerRequest.getRoles().isEmpty()
                        ? registerRequest.getRoles()
                        : Arrays.asList("USER"))
                .build();

        userService.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.extractUsername(token);
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        return ResponseEntity.ok(new MessageResponse("Token is valid"));
                    }
                }
            }
            return ResponseEntity.status(401).body(new MessageResponse("Invalid token"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new MessageResponse("Invalid token"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(new MessageResponse("Not authenticated"));
        }

        User userDetails = (User) authentication.getPrincipal();

        // Fetch project name if user has a project assigned
        String projectName = null;
        if (userDetails.getProjectId() != null && !userDetails.getProjectId().isEmpty()) {
            try {
                projectName = projectConfigurationService.getConfiguration(userDetails.getProjectId())
                        .map(ProjectConfiguration::getProjectName)
                        .orElse(null);
            } catch (Exception e) {
                // Log warning but don't fail the request
                projectName = null;
            }
        }

        // Build complete user response with all details
        Map<String, Object> response = new HashMap<>();
        response.put("id", userDetails.getId());
        response.put("username", userDetails.getUsername());
        response.put("email", userDetails.getEmail());
        response.put("roles", userDetails.getRoles());
        response.put("projectId", userDetails.getProjectId());
        response.put("projectName", projectName);
        response.put("enableToUseDevops", userDetails.getEnableToUseDevops());
        response.put("enabled", userDetails.isEnabled());
        response.put("createdAt", userDetails.getCreatedAt());
        response.put("updatedAt", userDetails.getUpdatedAt());

        return ResponseEntity.ok(response);
    }
}

