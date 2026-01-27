package com.devops.agent.service;

import com.devops.agent.model.ProjectConfiguration;
import com.devops.agent.model.VulnerabilityDetailDto;
import com.devops.agent.model.VulnerabilitySummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.inspector2.Inspector2Client;
import software.amazon.awssdk.services.inspector2.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsInspectorService {

    private final Inspector2Client inspector2Client; // Default client for backward compatibility
    private final ProjectConfigurationService projectConfigurationService;
    private final SecretsManagerService secretsManagerService;

    /**
     * Create Inspector2Client for a specific project using stored credentials
     */
    private Inspector2Client createProjectInspectorClient(String projectId) {
        log.info("Creating Inspector2Client for projectId: {}", projectId);

        try {
            // Get project configuration
            ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                throw new RuntimeException("Project is disabled: " + projectId);
            }

            // Get credentials from Secrets Manager
            Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
            String awsAccessKey = secrets.get("aws-access-key");
            String awsSecretKey = secrets.get("aws-secret-key");

            // Build Inspector2Client with project-specific credentials
            Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

            Inspector2Client client;
            // Use project-specific credentials if provided, otherwise use default
            if (awsAccessKey != null && awsSecretKey != null && !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for projectId: {}", projectId);
                AwsBasicCredentials credentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
                client = Inspector2Client.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build();
            } else {
                log.debug("Using default AWS credentials for projectId: {}", projectId);
                client = Inspector2Client.builder()
                        .region(region)
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            }

            return client;

        } catch (Exception e) {
            log.error("Failed to create Inspector2Client for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to create Inspector2Client for project: " + projectId, e);
        }
    }

    /**
     * Get all vulnerabilities from AWS Inspector2 for a specific project
     * Cached for 5 minutes to reduce API calls
     */
    @Cacheable(value = "vulnerabilities", key = "#projectId")
    public List<VulnerabilitySummaryDto> getAllVulnerabilitiesForProject(String projectId) {
        log.info("Fetching vulnerabilities for projectId: {} (cache miss)", projectId);

        // Get project configuration to extract AWS Account ID
        ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        try (Inspector2Client client = createProjectInspectorClient(projectId)) {
            return getAllVulnerabilitiesWithClient(client, config.getAwsAccountId());
        }
    }

    /**
     * Get vulnerability by ID for a specific project
     * Cached for 5 minutes to reduce API calls
     */
    @Cacheable(value = "vulnerabilityDetails", key = "#projectId + '_' + #id")
    public VulnerabilityDetailDto getVulnerabilityByIdForProject(String projectId, String id) {
        log.info("Fetching vulnerability {} for projectId: {} (cache miss)", id, projectId);

        // Get project configuration to extract AWS Account ID
        ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        try (Inspector2Client client = createProjectInspectorClient(projectId)) {
            return getVulnerabilityByIdWithClient(client, id, config.getAwsAccountId());
        }
    }

    /**
     * Get all vulnerabilities from AWS Inspector2 (using default credentials - backward compatibility)
     * Falls back to dummy data if AWS credentials are not configured or API fails
     */
    public List<VulnerabilitySummaryDto> getAllVulnerabilities() {
        return getAllVulnerabilitiesWithClient(inspector2Client, null);
    }

    /**
     * Get all vulnerabilities using a specific Inspector2Client
     */
    private List<VulnerabilitySummaryDto> getAllVulnerabilitiesWithClient(Inspector2Client client, String awsAccountId) {
        // Try to log region from client to ensure it's aligned with CLI
        try {
            String region = client.serviceClientConfiguration().region().id();
            log.info("Inspector2 client region: {}", region);
        } catch (Exception ignored) {
            log.debug("Could not determine Inspector2 client region from SDK");
        }

        log.info("Fetching all ACTIVE vulnerabilities from AWS Inspector2");

        List<VulnerabilitySummaryDto> result = new ArrayList<>();
        String nextToken = null;

        try {
            do {
                // Build filter: findingStatus = ACTIVE (same as CLI)
                FilterCriteria.Builder filterBuilder = FilterCriteria.builder()
                        .findingStatus(List.of(
                                StringFilter.builder()
                                        .comparison(StringComparison.EQUALS) // use enum instead of raw string
                                        .value("ACTIVE")
                                        .build()
                        ));

                // Add account ID filter if provided
                if (awsAccountId != null && !awsAccountId.isEmpty()) {
                    filterBuilder.awsAccountId(List.of(
                            StringFilter.builder()
                                    .comparison(StringComparison.EQUALS)
                                    .value(awsAccountId)
                                    .build()
                    ));
                    log.debug("Adding AWS Account ID filter: {}", awsAccountId);
                }

                FilterCriteria filterCriteria = filterBuilder.build();

                ListFindingsRequest.Builder requestBuilder = ListFindingsRequest.builder()
                        .maxResults(100)
                        .filterCriteria(filterCriteria);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                ListFindingsRequest request = requestBuilder.build();

                log.debug("Calling Inspector2 listFindings with filterCriteria={}, nextToken={}", filterCriteria, nextToken);
                ListFindingsResponse response = client.listFindings(request);

                if (response != null && response.findings() != null && !response.findings().isEmpty()) {
                    response.findings().stream()
                            .map(this::mapFindingToSummary)
                            .forEach(result::add);
                    log.debug("Fetched {} findings in current page", response.findings().size());
                } else {
                    log.debug("Empty findings page. response={}, nextToken={}, response.nextToken={}", response, nextToken,
                            response != null ? response.nextToken() : null);
                }

                nextToken = (response != null) ? response.nextToken() : null;
            } while (nextToken != null);

            if (result.isEmpty()) {
                log.warn("No ACTIVE findings returned from AWS Inspector2. Ensure the client region targets where findings exist (e.g., eu-west-1) and Inspector2 is enabled.");
                return getFallbackVulnerabilities();
            }

            log.info("Fetched {} ACTIVE vulnerabilities from AWS Inspector2", result.size());
            return result;

        } catch (Inspector2Exception e) {
            log.error("AWS Inspector2 error: {} (statusCode={})", e.awsErrorDetails().errorMessage(), e.awsErrorDetails().sdkHttpResponse().statusCode());
            log.warn("Falling back to dummy data due to AWS Inspector2 error");
            return getFallbackVulnerabilities();
        } catch (Exception e) {
            log.error("Unexpected error fetching vulnerabilities: {}", e.getMessage(), e);
            log.warn("Falling back to dummy data due to unexpected error");
            return getFallbackVulnerabilities();
        }
    }


    /**
     * Get vulnerability details by ID (backward compatibility - uses default credentials)
     */
    public VulnerabilityDetailDto getVulnerabilityById(String id) {
        return getVulnerabilityByIdWithClient(inspector2Client, id, null);
    }

    /**
     * Get vulnerability details by ID using a specific Inspector2Client (paginated search across all ACTIVE findings)
     */
    private VulnerabilityDetailDto getVulnerabilityByIdWithClient(Inspector2Client client, String id, String awsAccountId) {
        log.info("Fetching vulnerability by ID across all pages: {}", id);

        if (id == null || id.trim().isEmpty()) {
            log.error("Vulnerability ID cannot be null or empty");
            throw new IllegalArgumentException("Vulnerability ID cannot be null or empty");
        }

        String nextToken = null;
        int page = 0;
        try {
            do {
                page++;
                FilterCriteria filterCriteria = buildActiveFilterCriteria(awsAccountId);
                ListFindingsRequest.Builder requestBuilder = ListFindingsRequest.builder()
                        .maxResults(100)
                        .filterCriteria(filterCriteria);
                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }
                ListFindingsRequest request = requestBuilder.build();
                log.debug("[getVulnerabilityById] Page {} calling listFindings nextToken={} filterCriteria={}", page, nextToken, filterCriteria);
                ListFindingsResponse response = client.listFindings(request);

                if (response != null && response.findings() != null && !response.findings().isEmpty()) {
                    for (Finding finding : response.findings()) {
                        String arn = finding.findingArn();
                        String shortId = extractFindingShortId(finding);
                        if ((shortId != null && shortId.equals(id)) || (arn != null && arn.equals(id))) {
                            log.info("Found vulnerability ID {} on page {}", id, page);
                            return mapFindingToDetail(finding);
                        }
                    }
                } else {
                    log.debug("[getVulnerabilityById] Page {} returned no findings", page);
                }

                nextToken = (response != null) ? response.nextToken() : null;
            } while (nextToken != null);

            log.warn("Vulnerability with ID {} not found across {} pages. Returning fallback.", id, page);
            return getFallbackVulnerabilityDetail(id);
        } catch (Inspector2Exception e) {
            log.error("Inspector2 error while searching ID {}: {} (statusCode={})", id, e.awsErrorDetails().errorMessage(), e.awsErrorDetails().sdkHttpResponse().statusCode());
            return getFallbackVulnerabilityDetail(id);
        } catch (Exception e) {
            log.error("Unexpected error searching vulnerability ID {}: {}", id, e.getMessage(), e);
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
        String shortId = extractFindingShortId(finding);
        String cveId = extractCveId(vulnDetails);
        String packageName = extractPackageName(vulnDetails);
        String currentVersion = extractCurrentVersion(vulnDetails);
        String fixedVersion = extractFixedVersion(vulnDetails);

        return VulnerabilitySummaryDto.builder()
                .id(shortId)
                .cveId(cveId)
                .title(extractTitle(finding))
                .cwe(extractCweFromId(shortId))
                .severity(extractSeverity(finding))
                .cvssScore(extractCvssScore(vulnDetails))
                .packageName(packageName)
                .currentVersion(currentVersion)
                .fixedVersion(fixedVersion)
                .affectedProjects(extractAffectedProjects(finding))
                .status(extractStatus(finding))
                .lastUpdated(extractLastUpdatedDate(finding))
                .build();
    }

    private Date extractLastUpdatedDate(Finding finding) {
        if (finding.updatedAt() != null) {
            return Date.from(finding.updatedAt());
        }
        return new Date();
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
                .id(extractFindingShortId(finding))
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

    /**
     * Extract the short ID (suffix after 'finding/') from the ARN
     */
    private String extractFindingShortId(Finding finding) {
        String arn = finding.findingArn();
        if (arn == null || arn.isEmpty()) {
            return "unknown-id";
        }
        int idx = arn.lastIndexOf("finding/");
        if (idx != -1) {
            return arn.substring(idx + "finding/".length());
        }
        // Fallback: if pattern not found, return the full arn (unlikely)
        return arn;
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

    /** Helper to build ACTIVE findings filter (shared) */
    private FilterCriteria buildActiveFilterCriteria(String awsAccountId) {
        FilterCriteria.Builder filterBuilder = FilterCriteria.builder()
                .findingStatus(List.of(
                        StringFilter.builder()
                                .comparison(StringComparison.EQUALS)
                                .value("ACTIVE")
                                .build()
                ));

        // Add account ID filter if provided
        if (awsAccountId != null && !awsAccountId.isEmpty()) {
            filterBuilder.awsAccountId(List.of(
                    StringFilter.builder()
                            .comparison(StringComparison.EQUALS)
                            .value(awsAccountId)
                            .build()
            ));
        }

        return filterBuilder.build();
    }

    /**
     * Extract CWE as first 3 letters/digits from the short finding ID
     */
    private String extractCweFromId(String shortId) {
        if (shortId == null || shortId.isBlank()) {
            return "N/A";
        }
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < shortId.length() && sb.length() < 3; i++) {
            char ch = shortId.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toUpperCase(ch));
            }
        }
        return sb.length() == 0 ? "N/A" : sb.toString();
    }

    /**
     * Clear vulnerability cache for a specific project
     * Useful after vulnerability scans or when manual refresh is needed
     */
    @CacheEvict(value = "vulnerabilities", key = "#projectId")
    public void clearVulnerabilitiesCache(String projectId) {
        log.info("Clearing vulnerabilities cache for projectId: {}", projectId);
    }

    /**
     * Clear vulnerability details cache for a specific vulnerability in a project
     */
    @CacheEvict(value = "vulnerabilityDetails", key = "#projectId + '_' + #vulnerabilityId")
    public void clearVulnerabilityDetailsCache(String projectId, String vulnerabilityId) {
        log.info("Clearing vulnerability details cache for projectId: {} and vulnerabilityId: {}",
                projectId, vulnerabilityId);
    }

    /**
     * Clear all vulnerability caches for a specific project
     */
    @CacheEvict(value = {"vulnerabilities", "vulnerabilityDetails"}, allEntries = true)
    public void clearAllVulnerabilityCaches() {
        log.info("Clearing all vulnerability caches");
    }
}
