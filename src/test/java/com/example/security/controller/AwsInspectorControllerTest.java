package com.example.security.controller;

import com.devops.agent.DevOpsAgentApplication;
import com.devops.agent.controller.AwsInspectorController;
import com.devops.agent.model.VulnerabilityDetailDto;
import com.devops.agent.model.VulnerabilitySummaryDto;
import com.devops.agent.service.AwsInspectorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AwsInspectorController.class)
@ContextConfiguration(classes = DevOpsAgentApplication.class)
class AwsInspectorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AwsInspectorService awsInspectorService;

    @Test
    void testGetAllVulnerabilities_Success() throws Exception {
        // Arrange
        VulnerabilitySummaryDto vuln1 = VulnerabilitySummaryDto.builder()
                .id("CVE-2025-55754-001")
                .cveId("CVE-2025-55754")
                .title("Remote Code Execution in Tomcat Embed Core")
                .cwe("CWE-94")
                .severity("CRITICAL")
                .cvssScore(9.8)
                .packageName("org.apache.tomcat.embed:tomcat-embed-core")
                .currentVersion("9.0.65")
                .fixedVersion("9.0.70")
                .affectedProjects(2)
                .status("Open")
                .build();

        VulnerabilitySummaryDto vuln2 = VulnerabilitySummaryDto.builder()
                .id("CVE-2025-12345-002")
                .cveId("CVE-2025-12345")
                .title("SQL Injection in Spring Data JPA")
                .cwe("CWE-89")
                .severity("HIGH")
                .cvssScore(8.5)
                .packageName("org.springframework.data:spring-data-jpa")
                .currentVersion("2.5.0")
                .fixedVersion("2.5.6")
                .affectedProjects(1)
                .status("Open")
                .build();

        List<VulnerabilitySummaryDto> vulnerabilities = Arrays.asList(vuln1, vuln2);
        when(awsInspectorService.getAllVulnerabilities()).thenReturn(vulnerabilities);

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("CVE-2025-55754-001"))
                .andExpect(jsonPath("$[0].cveId").value("CVE-2025-55754"))
                .andExpect(jsonPath("$[0].title").value("Remote Code Execution in Tomcat Embed Core"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].cvssScore").value(9.8))
                .andExpect(jsonPath("$[0].packageName").value("org.apache.tomcat.embed:tomcat-embed-core"))
                .andExpect(jsonPath("$[1].id").value("CVE-2025-12345-002"))
                .andExpect(jsonPath("$[1].cveId").value("CVE-2025-12345"))
                .andExpect(jsonPath("$[1].title").value("SQL Injection in Spring Data JPA"));
    }

    @Test
    void testGetAllVulnerabilities_EmptyList() throws Exception {
        // Arrange
        when(awsInspectorService.getAllVulnerabilities()).thenReturn(Arrays.asList());

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetAllVulnerabilities_Error() throws Exception {
        // Arrange
        when(awsInspectorService.getAllVulnerabilities()).thenThrow(new RuntimeException("AWS Inspector error"));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetVulnerabilityById_Success() throws Exception {
        // Arrange
        VulnerabilityDetailDto vuln = VulnerabilityDetailDto.builder()
                .id("CVE-2025-55754-001")
                .cveId("CVE-2025-55754")
                .title("Remote Code Execution in Tomcat Embed Core")
                .cwe("CWE-94")
                .severity("CRITICAL")
                .cvssScore(9.8)
                .packageName("org.apache.tomcat.embed:tomcat-embed-core")
                .currentVersion("9.0.65")
                .fixedVersion("9.0.70")
                .affectedProjects(2)
                .status("Open")
                .description("A critical remote code execution vulnerability was discovered in Apache Tomcat Embed Core. " +
                        "An attacker can exploit this vulnerability to execute arbitrary code on the server.")
                .publishedDate("2025-01-15T00:00:00Z")
                .references(Arrays.asList(
                        "https://nvd.nist.gov/vuln/detail/CVE-2025-55754",
                        "https://tomcat.apache.org/security-9.html"
                ))
                .build();

        when(awsInspectorService.getVulnerabilityById("CVE-2025-55754-001")).thenReturn(vuln);

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/CVE-2025-55754-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("CVE-2025-55754-001"))
                .andExpect(jsonPath("$.cveId").value("CVE-2025-55754"))
                .andExpect(jsonPath("$.title").value("Remote Code Execution in Tomcat Embed Core"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.cvssScore").value(9.8))
                .andExpect(jsonPath("$.description").value("A critical remote code execution vulnerability was discovered in Apache Tomcat Embed Core. " +
                        "An attacker can exploit this vulnerability to execute arbitrary code on the server."))
                .andExpect(jsonPath("$.publishedDate").value("2025-01-15T00:00:00Z"))
                .andExpect(jsonPath("$.references.length()").value(2))
                .andExpect(jsonPath("$.references[0]").value("https://nvd.nist.gov/vuln/detail/CVE-2025-55754"));
    }

    @Test
    void testGetVulnerabilityById_NotFound() throws Exception {
        // Arrange
        when(awsInspectorService.getVulnerabilityById(anyString())).thenThrow(new RuntimeException("Vulnerability not found"));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/INVALID-ID-999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetVulnerabilityById_InvalidArgument() throws Exception {
        // Arrange
        when(awsInspectorService.getVulnerabilityById("")).thenThrow(new IllegalArgumentException("Vulnerability ID cannot be null or empty"));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // Spring treats empty path segment as different endpoint
    }

    @Test
    void testGetVulnerabilityById_InvalidArgumentWithNullId() throws Exception {
        // Arrange
        when(awsInspectorService.getVulnerabilityById(anyString())).thenThrow(new IllegalArgumentException("Invalid ID"));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/null")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetVulnerabilityById_RuntimeException() throws Exception {
        // Arrange
        String testId = "TEST-ID-ERROR";
        // RuntimeExceptions (except IllegalArgumentException) return 404
        when(awsInspectorService.getVulnerabilityById(testId)).thenThrow(new NullPointerException("Unexpected runtime error"));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/" + testId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetVulnerabilityById_WithSpecialCharacters() throws Exception {
        // Arrange
        String idWithHyphens = "CVE-2025-99999-finding-abc-123";
        VulnerabilityDetailDto vuln = VulnerabilityDetailDto.builder()
                .id(idWithHyphens)
                .cveId("CVE-2025-99999")
                .title("Test Vulnerability")
                .cwe("CWE-79")
                .severity("MEDIUM")
                .cvssScore(6.5)
                .packageName("test-package")
                .currentVersion("1.0.0")
                .fixedVersion("1.0.1")
                .affectedProjects(1)
                .status("Open")
                .description("Test description")
                .publishedDate("2025-01-01T00:00:00Z")
                .references(Arrays.asList("https://example.com"))
                .build();

        when(awsInspectorService.getVulnerabilityById(idWithHyphens)).thenReturn(vuln);

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities/" + idWithHyphens)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idWithHyphens))
                .andExpect(jsonPath("$.cveId").value("CVE-2025-99999"));
    }

    @Test
    void testGetAllVulnerabilities_CheckAllFields() throws Exception {
        // Arrange
        VulnerabilitySummaryDto vuln = VulnerabilitySummaryDto.builder()
                .id("TEST-001")
                .cveId("CVE-2025-00001")
                .title("Test Vulnerability Title")
                .cwe("CWE-123")
                .severity("LOW")
                .cvssScore(3.5)
                .packageName("test.package:artifact")
                .currentVersion("1.2.3")
                .fixedVersion("1.2.4")
                .affectedProjects(5)
                .status("Resolved")
                .build();

        when(awsInspectorService.getAllVulnerabilities()).thenReturn(Arrays.asList(vuln));

        // Act & Assert
        mockMvc.perform(get("/api/vulnerabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("TEST-001"))
                .andExpect(jsonPath("$[0].cveId").value("CVE-2025-00001"))
                .andExpect(jsonPath("$[0].title").value("Test Vulnerability Title"))
                .andExpect(jsonPath("$[0].cwe").value("CWE-123"))
                .andExpect(jsonPath("$[0].severity").value("LOW"))
                .andExpect(jsonPath("$[0].cvssScore").value(3.5))
                .andExpect(jsonPath("$[0].packageName").value("test.package:artifact"))
                .andExpect(jsonPath("$[0].currentVersion").value("1.2.3"))
                .andExpect(jsonPath("$[0].fixedVersion").value("1.2.4"))
                .andExpect(jsonPath("$[0].affectedProjects").value(5))
                .andExpect(jsonPath("$[0].status").value("Resolved"));
    }
}
