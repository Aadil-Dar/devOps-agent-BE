package com.example.security.service;

import com.devops.agent.model.VulnerabilityDetailDto;
import com.devops.agent.model.VulnerabilitySummaryDto;
import com.devops.agent.service.AwsInspectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsInspectorServiceTest {

    @Mock
    private Inspector2Client inspector2Client;

    private AwsInspectorService awsInspectorService;

    @BeforeEach
    void setUp() {
        awsInspectorService = new AwsInspectorService(inspector2Client);
    }

    @Test
    void getAllVulnerabilities_shouldReturnMappedFindings_whenAWSReturnsData() {
        // Given
        Finding mockFinding = createMockFinding(
                "arn:aws:inspector2:us-east-1:123456789012:finding/abc123",
                "CVE-2023-12345",
                "Critical Security Vulnerability",
                Severity.CRITICAL,
                FindingStatus.ACTIVE
        );

        ListFindingsResponse mockResponse = ListFindingsResponse.builder()
                .findings(mockFinding)
                .build();

        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(mockResponse);

        // When
        List<VulnerabilitySummaryDto> result = awsInspectorService.getAllVulnerabilities();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        VulnerabilitySummaryDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("arn:aws:inspector2:us-east-1:123456789012:finding/abc123");
        assertThat(dto.getCveId()).isEqualTo("CVE-2023-12345");
        assertThat(dto.getTitle()).isEqualTo("Critical Security Vulnerability");
        assertThat(dto.getSeverity()).isEqualTo("CRITICAL");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getAllVulnerabilities_shouldReturnFallbackData_whenAWSThrowsException() {
        // Given
        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenThrow(Inspector2Exception.builder()
                        .message("AWS credentials not configured")
                        .build());

        // When
        List<VulnerabilitySummaryDto> result = awsInspectorService.getAllVulnerabilities();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        VulnerabilitySummaryDto fallback = result.get(0);
        assertThat(fallback.getCveId()).isEqualTo("CVE-2025-55754");
        assertThat(fallback.getTitle()).isEqualTo("Remote Code Execution in Tomcat Embed Core");
        assertThat(fallback.getCwe()).isEqualTo("CWE-94");
        assertThat(fallback.getSeverity()).isEqualTo("CRITICAL");
        assertThat(fallback.getCvssScore()).isEqualTo(9.8);
        assertThat(fallback.getPackageName()).isEqualTo("org.apache.tomcat.embed:tomcat-embed-core");
        assertThat(fallback.getCurrentVersion()).isEqualTo("9.0.65");
        assertThat(fallback.getFixedVersion()).isEqualTo("9.0.70");
        assertThat(fallback.getAffectedProjects()).isEqualTo(2);
        assertThat(fallback.getStatus()).isEqualTo("Open");
    }

    @Test
    void getAllVulnerabilities_shouldReturnFallbackData_whenAWSReturnsNull() {
        // Given
        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(null);

        // When
        List<VulnerabilitySummaryDto> result = awsInspectorService.getAllVulnerabilities();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCveId()).isEqualTo("CVE-2025-55754");
    }

    @Test
    void getAllVulnerabilities_shouldReturnFallbackData_whenAWSReturnsEmptyFindings() {
        // Given
        ListFindingsResponse mockResponse = ListFindingsResponse.builder()
                .findings(Collections.emptyList())
                .build();

        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(mockResponse);

        // When
        List<VulnerabilitySummaryDto> result = awsInspectorService.getAllVulnerabilities();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCveId()).isEqualTo("CVE-2025-55754");
    }

    @Test
    void getVulnerabilityById_shouldReturnMappedFinding_whenFoundInAWS() {
        // Given
        String findingArn = "arn:aws:inspector2:us-east-1:123456789012:finding/abc123";
        Finding mockFinding = createMockFinding(
                findingArn,
                "CVE-2023-12345",
                "Critical Security Vulnerability",
                Severity.CRITICAL,
                FindingStatus.ACTIVE
        );

        ListFindingsResponse mockResponse = ListFindingsResponse.builder()
                .findings(mockFinding)
                .build();

        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(mockResponse);

        // When
        VulnerabilityDetailDto result = awsInspectorService.getVulnerabilityById(findingArn);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(findingArn);
        assertThat(result.getCveId()).isEqualTo("CVE-2023-12345");
        assertThat(result.getTitle()).isEqualTo("Critical Security Vulnerability");
        assertThat(result.getDescription()).isNotNull();
        assertThat(result.getReferences()).isNotNull();
    }

    @Test
    void getVulnerabilityById_shouldReturnFallbackData_whenNotFoundInAWS() {
        // Given
        String requestedId = "non-existent-id";
        Finding mockFinding = createMockFinding(
                "arn:aws:inspector2:us-east-1:123456789012:finding/different",
                "CVE-2023-12345",
                "Different Vulnerability",
                Severity.MEDIUM,
                FindingStatus.ACTIVE
        );

        ListFindingsResponse mockResponse = ListFindingsResponse.builder()
                .findings(mockFinding)
                .build();

        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(mockResponse);

        // When
        VulnerabilityDetailDto result = awsInspectorService.getVulnerabilityById(requestedId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(requestedId);
        assertThat(result.getCveId()).isEqualTo("CVE-2025-55754");
        assertThat(result.getTitle()).isEqualTo("Remote Code Execution in Tomcat Embed Core");
        assertThat(result.getDescription()).contains("critical remote code execution vulnerability");
        assertThat(result.getReferences()).hasSize(2);
        assertThat(result.getReferences()).contains(
                "https://nvd.nist.gov/vuln/detail/CVE-2025-55754",
                "https://tomcat.apache.org/security-9.html"
        );
    }

    @Test
    void getVulnerabilityById_shouldReturnFallbackData_whenAWSThrowsException() {
        // Given
        String id = "some-id";
        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenThrow(Inspector2Exception.builder()
                        .message("AWS error")
                        .build());

        // When
        VulnerabilityDetailDto result = awsInspectorService.getVulnerabilityById(id);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getCveId()).isEqualTo("CVE-2025-55754");
    }

    @Test
    void getVulnerabilityById_shouldThrowException_whenIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> awsInspectorService.getVulnerabilityById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vulnerability ID cannot be null or empty");
    }

    @Test
    void getVulnerabilityById_shouldThrowException_whenIdIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> awsInspectorService.getVulnerabilityById(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vulnerability ID cannot be null or empty");
    }

    @Test
    void getVulnerabilityById_shouldThrowException_whenIdIsBlank() {
        // When & Then
        assertThatThrownBy(() -> awsInspectorService.getVulnerabilityById("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vulnerability ID cannot be null or empty");
    }

    @Test
    void getAllVulnerabilities_shouldHandleMultipleFindings() {
        // Given
        Finding finding1 = createMockFinding(
                "arn:1",
                "CVE-2023-11111",
                "Vulnerability 1",
                Severity.HIGH,
                FindingStatus.ACTIVE
        );
        Finding finding2 = createMockFinding(
                "arn:2",
                "CVE-2023-22222",
                "Vulnerability 2",
                Severity.MEDIUM,
                FindingStatus.SUPPRESSED
        );

        ListFindingsResponse mockResponse = ListFindingsResponse.builder()
                .findings(finding1, finding2)
                .build();

        when(inspector2Client.listFindings(any(ListFindingsRequest.class)))
                .thenReturn(mockResponse);

        // When
        List<VulnerabilitySummaryDto> result = awsInspectorService.getAllVulnerabilities();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCveId()).isEqualTo("CVE-2023-11111");
        assertThat(result.get(1).getCveId()).isEqualTo("CVE-2023-22222");
    }

    // Helper method to create mock Finding objects
    private Finding createMockFinding(String arn, String cveId, String title, 
                                     Severity severity, FindingStatus status) {
        
        VulnerablePackage vulnerablePackage = VulnerablePackage.builder()
                .name("test-package")
                .version("1.0.0")
                .fixedInVersion("1.0.1")
                .build();

        CvssScore cvssScore = CvssScore.builder()
                .baseScore(7.5)
                .build();

        PackageVulnerabilityDetails vulnDetails = PackageVulnerabilityDetails.builder()
                .vulnerabilityId(cveId)
                .vulnerablePackages(vulnerablePackage)
                .cvss(cvssScore)
                .relatedVulnerabilities("CWE-79")
                .referenceUrls("https://example.com/vuln")
                .build();

        Resource resource = Resource.builder()
                .id("resource-1")
                .build();

        return Finding.builder()
                .findingArn(arn)
                .title(title)
                .description("Test description for " + title)
                .severity(severity)
                .status(status)
                .packageVulnerabilityDetails(vulnDetails)
                .resources(resource)
                .firstObservedAt(Instant.now())
                .build();
    }
}
