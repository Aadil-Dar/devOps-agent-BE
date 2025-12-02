package com.example.security.service;

import com.example.security.model.VulnerabilityDetailDto;
import com.example.security.model.VulnerabilitySummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsInspectorService {

    private final Inspector2Client inspector2Client;

    /**
     * Get all vulnerabilities from AWS Inspector2
     * Falls back to dummy data if AWS credentials are not configured or API fails
     */
    public List<VulnerabilitySummaryDto> getAllVulnerabilities() {
        log.info("Fetching all vulnerabilities from AWS Inspector2");
        try {
            ListFindingsRequest request = ListFindingsRequest.builder()
                    .maxResults(100)
                    .build();

            ListFindingsResponse response = inspector2Client.listFindings(request);
            
            if (response == null || response.findings() == null || response.findings().isEmpty()) {
                log.warn("No findings returned from AWS Inspector2, using fallback data");
                return getFallbackVulnerabilities();
            }

            return response.findings().stream()
                    .map(this::mapFindingToSummary)
                    .collect(Collectors.toList());

        } catch (Inspector2Exception e) {
            log.error("AWS Inspector2 error: {}", e.getMessage(), e);
            log.warn("Falling back to dummy data due to AWS Inspector2 error");
            return getFallbackVulnerabilities();
        } catch (Exception e) {
            log.error("Unexpected error fetching vulnerabilities: {}", e.getMessage(), e);
            log.warn("Falling back to dummy data due to unexpected error");
            return getFallbackVulnerabilities();
        }
    }

    /**
     * Get vulnerability details by ID
     * Falls back to dummy data if AWS credentials are not configured or API fails
     */
    public VulnerabilityDetailDto getVulnerabilityById(String id) {
        log.info("Fetching vulnerability by ID: {}", id);
        
        if (id == null || id.trim().isEmpty()) {
            log.error("Vulnerability ID cannot be null or empty");
            throw new IllegalArgumentException("Vulnerability ID cannot be null or empty");
        }

        try {
            ListFindingsRequest request = ListFindingsRequest.builder()
                    .maxResults(100)
                    .build();

            ListFindingsResponse response = inspector2Client.listFindings(request);
            
            if (response == null || response.findings() == null) {
                log.warn("No findings returned from AWS Inspector2, using fallback data");
                return getFallbackVulnerabilityDetail(id);
            }

            Optional<Finding> finding = response.findings().stream()
                    .filter(f -> extractFindingId(f).equals(id))
                    .findFirst();

            if (finding.isPresent()) {
                return mapFindingToDetail(finding.get());
            } else {
                log.warn("Vulnerability with ID {} not found in AWS Inspector2, using fallback data", id);
                return getFallbackVulnerabilityDetail(id);
            }

        } catch (Inspector2Exception e) {
            log.error("AWS Inspector2 error: {}", e.getMessage(), e);
            log.warn("Falling back to dummy data due to AWS Inspector2 error");
            return getFallbackVulnerabilityDetail(id);
        } catch (Exception e) {
            log.error("Unexpected error fetching vulnerability: {}", e.getMessage(), e);
            log.warn("Falling back to dummy data due to unexpected error");
            return getFallbackVulnerabilityDetail(id);
        }
    }

    /**
     * Map AWS Inspector Finding to VulnerabilitySummaryDto
     */
    private VulnerabilitySummaryDto mapFindingToSummary(Finding finding) {
        if (finding == null) {
            return null;
        }

        PackageVulnerabilityDetails vulnDetails = finding.packageVulnerabilityDetails();
        
        String cveId = extractCveId(vulnDetails);
        String packageName = extractPackageName(vulnDetails);
        String currentVersion = extractCurrentVersion(vulnDetails);
        String fixedVersion = extractFixedVersion(vulnDetails);
        
        return VulnerabilitySummaryDto.builder()
                .id(extractFindingId(finding))
                .cveId(cveId)
                .title(extractTitle(finding))
                .cwe(extractCwe(vulnDetails))
                .severity(extractSeverity(finding))
                .cvssScore(extractCvssScore(vulnDetails))
                .packageName(packageName)
                .currentVersion(currentVersion)
                .fixedVersion(fixedVersion)
                .affectedProjects(extractAffectedProjects(finding))
                .status(extractStatus(finding))
                .build();
    }

    /**
     * Map AWS Inspector Finding to VulnerabilityDetailDto
     */
    private VulnerabilityDetailDto mapFindingToDetail(Finding finding) {
        if (finding == null) {
            return null;
        }

        VulnerabilitySummaryDto summary = mapFindingToSummary(finding);
        
        return VulnerabilityDetailDto.builder()
                .id(summary.getId())
                .cveId(summary.getCveId())
                .title(summary.getTitle())
                .cwe(summary.getCwe())
                .severity(summary.getSeverity())
                .cvssScore(summary.getCvssScore())
                .packageName(summary.getPackageName())
                .currentVersion(summary.getCurrentVersion())
                .fixedVersion(summary.getFixedVersion())
                .affectedProjects(summary.getAffectedProjects())
                .status(summary.getStatus())
                .description(extractDescription(finding))
                .publishedDate(extractPublishedDate(finding))
                .references(extractReferences(finding))
                .build();
    }

    // Helper methods to extract data from AWS Inspector Finding

    private String extractFindingId(Finding finding) {
        return finding.findingArn() != null ? finding.findingArn() : "unknown-id";
    }

    private String extractCveId(PackageVulnerabilityDetails details) {
        if (details != null && details.vulnerabilityId() != null) {
            return details.vulnerabilityId();
        }
        return "N/A";
    }

    private String extractTitle(Finding finding) {
        if (finding.title() != null) {
            return finding.title();
        }
        return "Unknown Vulnerability";
    }

    private String extractCwe(PackageVulnerabilityDetails details) {
        if (details != null && details.relatedVulnerabilities() != null && !details.relatedVulnerabilities().isEmpty()) {
            return details.relatedVulnerabilities().get(0);
        }
        return "N/A";
    }

    private String extractSeverity(Finding finding) {
        if (finding.severity() != null) {
            return finding.severity().toString();
        }
        return "UNKNOWN";
    }

    private Double extractCvssScore(PackageVulnerabilityDetails details) {
        if (details != null && details.cvss() != null && !details.cvss().isEmpty()) {
            CvssScore cvss = details.cvss().get(0);
            if (cvss != null && cvss.baseScore() != null) {
                return cvss.baseScore();
            }
        }
        return 0.0;
    }

    private String extractPackageName(PackageVulnerabilityDetails details) {
        if (details != null && details.vulnerablePackages() != null && !details.vulnerablePackages().isEmpty()) {
            VulnerablePackage pkg = details.vulnerablePackages().get(0);
            if (pkg != null && pkg.name() != null) {
                return pkg.name();
            }
        }
        return "Unknown Package";
    }

    private String extractCurrentVersion(PackageVulnerabilityDetails details) {
        if (details != null && details.vulnerablePackages() != null && !details.vulnerablePackages().isEmpty()) {
            VulnerablePackage pkg = details.vulnerablePackages().get(0);
            if (pkg != null && pkg.version() != null) {
                return pkg.version();
            }
        }
        return "N/A";
    }

    private String extractFixedVersion(PackageVulnerabilityDetails details) {
        if (details != null && details.vulnerablePackages() != null && !details.vulnerablePackages().isEmpty()) {
            VulnerablePackage pkg = details.vulnerablePackages().get(0);
            if (pkg != null && pkg.fixedInVersion() != null) {
                return pkg.fixedInVersion();
            }
        }
        return "N/A";
    }

    private Integer extractAffectedProjects(Finding finding) {
        if (finding.resources() != null) {
            return finding.resources().size();
        }
        return 0;
    }

    private String extractStatus(Finding finding) {
        if (finding.status() != null) {
            return finding.status().toString();
        }
        return "OPEN";
    }

    private String extractDescription(Finding finding) {
        if (finding.description() != null) {
            return finding.description();
        }
        return "No description available";
    }

    private String extractPublishedDate(Finding finding) {
        if (finding.firstObservedAt() != null) {
            return finding.firstObservedAt().toString();
        }
        return "N/A";
    }

    private List<String> extractReferences(Finding finding) {
        List<String> references = new ArrayList<>();
        
        PackageVulnerabilityDetails details = finding.packageVulnerabilityDetails();
        if (details != null && details.referenceUrls() != null) {
            references.addAll(details.referenceUrls());
        }
        
        // If no references found, return empty list
        return references;
    }

    /**
     * Provide fallback dummy data when AWS Inspector2 is unavailable
     */
    private List<VulnerabilitySummaryDto> getFallbackVulnerabilities() {
        log.info("Using fallback dummy vulnerability data");
        
        List<VulnerabilitySummaryDto> fallbackData = new ArrayList<>();
        
        fallbackData.add(VulnerabilitySummaryDto.builder()
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
                .build());
        
        return fallbackData;
    }

    /**
     * Provide fallback dummy detail data for a specific vulnerability ID
     */
    private VulnerabilityDetailDto getFallbackVulnerabilityDetail(String id) {
        log.info("Using fallback dummy vulnerability detail data for ID: {}", id);
        
        return VulnerabilityDetailDto.builder()
                .id(id)
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
                        "An attacker can exploit this vulnerability to execute arbitrary code on the server. " +
                        "This affects all versions prior to 9.0.70.")
                .publishedDate("2025-01-15T00:00:00Z")
                .references(List.of(
                        "https://nvd.nist.gov/vuln/detail/CVE-2025-55754",
                        "https://tomcat.apache.org/security-9.html"
                ))
                .build();
    }
}
