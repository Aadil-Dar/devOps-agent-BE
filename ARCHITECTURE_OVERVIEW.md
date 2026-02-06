# DevOps Agent – High-Level Architecture & AWS Deployment

## Overview
- Purpose: Spring Boot 3.2 (Java 17) backend that surfaces DevOps signals (AWS CodePipeline, CloudWatch alarms, AWS Inspector2 vulnerabilities, GitHub PR status) and AI-driven insights over HTTP APIs.
- Security: JWT authentication with role-based access control (ADMIN/USER); health and auth endpoints exempted accordingly.
- Integrations: AWS SDK v2 (CodePipeline, CloudWatch, Inspector2), GitHub API (kohsuke), optional Ollama LLM for AI insights; falls back to dummy data when AWS creds are absent for Inspector2.
- Packaging: Gradle-built fat JAR; containerized via Docker; configurable profiles (`dev`, `prod`).

## Core Components
- API Layer (controllers): Pipeline, Alarm, AwsInspector, PullRequest, AiInsights endpoints under `/api/**` plus actuator health.
- Services: Business logic calling AWS/GitHub/LLM clients; DTO mapping and error handling; AI prompt/build/parse in `AiInsightsService`.
- Configuration: AWS and GitHub client beans, CORS, Ollama WebClient; Spring Security/JWT setup; profile-specific properties.
- Models/DTOs: Pipeline/Alarm/PR/Vulnerability/AiInsight request/response shapes; serialization-friendly objects.
- Infrastructure Support: Dockerfile, docker-compose, Gradle wrapper, environment-specific property files.

## Data Flow (example)
- Pipeline status: Client → `/api/pipelines/{name}` → Controller → `PipelineService` → AWS CodePipeline client → DTO response.
- Vulnerabilities: Client → `/api/vulnerabilities` → Controller → `AwsInspectorService` → Inspector2 list/findings → DTO; dummy data if creds missing.
- PR status: Client → `/api/pull-requests` → Controller → `GitHubService` → GitHub API → DTOs.
- AI insights: Client → `/api/ai-insights/analyze` → Controller → `AiInsightsService` → Ollama API (e.g., qwen2.5-coder) → AI remediation summary.

## Key Deployment Considerations
- Config: Provide `AWS_REGION`, `SPRING_PROFILES_ACTIVE=prod`, and GitHub repo/token vars as needed. Use AWS IAM roles for service access; no static credentials in code.
- Networking: Expose port 8080; prefer ALB with health check on `/actuator/health`; enable HTTPS via ACM; restrict inbound via SGs.
- Observability: Ship logs to CloudWatch Logs; set retention; add CPU/memory alarms; keep actuator health endpoint reachable by load balancer.
- Secrets: Store in AWS Secrets Manager or SSM Parameter Store; avoid baking secrets into images.

## AWS Deployment (concise steps)

### Option 1: ECS Fargate (recommended)
1) Build & image
```bash
./gradlew clean build
docker build -t devops-agent:latest .
```
2) Push to ECR (replace region/account)
```bash
aws ecr create-repository --repository-name devops-agent --region eu-west-1
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.eu-west-1.amazonaws.com
docker tag devops-agent:latest <ACCOUNT_ID>.dkr.ecr.eu-west-1.amazonaws.com/devops-agent:latest
docker push <ACCOUNT_ID>.dkr.ecr.eu-west-1.amazonaws.com/devops-agent:latest
```
3) Task definition (Fargate, `awsvpc`, CPU/mem sized for load; env: `AWS_REGION`, `SPRING_PROFILES_ACTIVE=prod`; logDriver `awslogs`).
4) IAM: Execution role for ECR pull/logs; task role granting CodePipeline/CloudWatch/Inspector2 (and DynamoDB/Secrets if used).
5) Network & LB: Create cluster; run service with `desiredCount>=2`, subnets + SG; ALB + target group (HC `/actuator/health`).
6) Deploy: `aws ecs create-service` or `update-service --force-new-deployment` to roll out new image.

### Option 2: EC2 (systemd-managed JAR)
1) Launch Amazon Linux 2/2023 instance with IAM instance profile allowing required AWS APIs.
2) Install Java 17; copy `build/libs/devops-agent-1.0.0.jar` (or S3 download) to `/opt/devops-agent/app.jar`.
3) Create `systemd` unit pointing to the JAR; set env (`AWS_REGION`, `SPRING_PROFILES_ACTIVE=prod`, GitHub vars); open port 8080 via SG/ALB.
4) Enable and start service; verify with `curl http://localhost:8080/actuator/health` (or through ALB).

### Option 3: Elastic Beanstalk (single-container)
1) Build JAR; create `Procfile`: `web: java -jar build/libs/devops-agent-1.0.0.jar`.
2) Add `.ebextensions/environment.config` for env vars and proxy config.
3) `eb init` (Java platform, prod region) → `eb create` → `eb deploy`.

## Post-Deployment Checklist
- Health: `/actuator/health` returns `UP` via ALB.
- Auth: JWT auth configured; default creds rotated; HTTPS enforced.
- Logs & Metrics: CloudWatch log group exists; retention set; CPU/mem alarms active.
- Scaling: ECS service or ASG policies set; min healthy tasks/instances defined.
- Backups: If using DynamoDB/Secrets, enable PITR/versioning as needed.

## Quick Reference (inputs to set)
- AWS: `AWS_REGION`, IAM roles for CodePipeline/CloudWatch/Inspector2 (+ any data stores).
- App: `SPRING_PROFILES_ACTIVE`, optional GitHub token/repo settings, port if not default.
- Networking: VPC subnets, SG IDs, ALB target group health path `/actuator/health`.
