package com.devops.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiInsightResponse {

    private String cveId;
    private String title;
    private String severity;
    private Double cvssScore;
    private String cwe;
    private String packageName;
    private String currentVersion;
    private String fixedVersion;
    private Integer affectedProjects;
    private String description;

    private String aiRemediationAnalysis;
    private String estimatedTime;   // e.g. "2-4 hours"
    private String riskLevel;       // e.g. "High"
    private Boolean automationAvailable;

    private List<String> recommendedRemediationSteps;
    private String mavenDependencyUpdateSnippet;
    private List<String> references;


    // getters and setters

    public String getCveId() {
        return cveId;
    }

    public void setCveId(String cveId) {
        this.cveId = cveId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Double getCvssScore() {
        return cvssScore;
    }

    public void setCvssScore(Double cvssScore) {
        this.cvssScore = cvssScore;
    }

    public String getCwe() {
        return cwe;
    }

    public void setCwe(String cwe) {
        this.cwe = cwe;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getFixedVersion() {
        return fixedVersion;
    }

    public void setFixedVersion(String fixedVersion) {
        this.fixedVersion = fixedVersion;
    }

    public Integer getAffectedProjects() {
        return affectedProjects;
    }

    public void setAffectedProjects(Integer affectedProjects) {
        this.affectedProjects = affectedProjects;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAiRemediationAnalysis() {
        return aiRemediationAnalysis;
    }

    public void setAiRemediationAnalysis(String aiRemediationAnalysis) {
        this.aiRemediationAnalysis = aiRemediationAnalysis;
    }

    public String getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(String estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getAutomationAvailable() {
        return automationAvailable;
    }

    public void setAutomationAvailable(Boolean automationAvailable) {
        this.automationAvailable = automationAvailable;
    }

    public List<String> getRecommendedRemediationSteps() {
        return recommendedRemediationSteps;
    }

    public void setRecommendedRemediationSteps(List<String> recommendedRemediationSteps) {
        this.recommendedRemediationSteps = recommendedRemediationSteps;
    }

    public String getMavenDependencyUpdateSnippet() {
        return mavenDependencyUpdateSnippet;
    }

    public void setMavenDependencyUpdateSnippet(String mavenDependencyUpdateSnippet) {
        this.mavenDependencyUpdateSnippet = mavenDependencyUpdateSnippet;
    }

    public List<String> getReferences() {
        return references;
    }

    public void setReferences(List<String> references) {
        this.references = references;
    }
}
