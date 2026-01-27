# AWS Deployment Guide

## Deployment Options

### Option 1: AWS ECS Fargate (Recommended)

#### Prerequisites
- AWS CLI configured
- Docker installed
- ECR repository created

#### Step 1: Create ECR Repository

```bash
aws ecr create-repository \
    --repository-name devops-agent \
    --region eu-west-1
```

#### Step 2: Build and Push Docker Image

```bash
# Build the application
./gradlew clean build

# Build Docker image
docker build -t devops-agent:latest .

# Tag for ECR
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin YOUR_ACCOUNT_ID.dkr.ecr.eu-west-1.amazonaws.com

docker tag devops-agent:latest YOUR_ACCOUNT_ID.dkr.ecr.eu-west-1.amazonaws.com/devops-agent:latest

# Push to ECR
docker push YOUR_ACCOUNT_ID.dkr.ecr.eu-west-1.amazonaws.com/devops-agent:latest
```

#### Step 3: Create ECS Task Definition

Create `task-definition.json`:

```json
{
  "family": "devops-agent",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::YOUR_ACCOUNT_ID:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::YOUR_ACCOUNT_ID:role/devops-agent-task-role",
  "containerDefinitions": [
    {
      "name": "devops-agent",
      "image": "YOUR_ACCOUNT_ID.dkr.ecr.eu-west-1.amazonaws.com/devops-agent:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "AWS_REGION",
          "value": "eu-west-1"
        },
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/devops-agent",
          "awslogs-region": "eu-west-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Step 4: Create IAM Role for ECS Task

Create `task-role-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:*"
      ],
      "Resource": "arn:aws:dynamodb:*:*:table/devops-projects"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:*"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:devops-agent/projects/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "codepipeline:*",
        "cloudwatch:*",
        "logs:*",
        "inspector2:*"
      ],
      "Resource": "*"
    }
  ]
}
```

Create the role:

```bash
# Create task role
aws iam create-role \
    --role-name devops-agent-task-role \
    --assume-role-policy-document file://trust-policy.json

# Attach policy
aws iam put-role-policy \
    --role-name devops-agent-task-role \
    --policy-name devops-agent-policy \
    --policy-document file://task-role-policy.json
```

#### Step 5: Create ECS Service

```bash
# Register task definition
aws ecs register-task-definition --cli-input-json file://task-definition.json

# Create ECS cluster
aws ecs create-cluster --cluster-name devops-agent-cluster --region eu-west-1

# Create service
aws ecs create-service \
    --cluster devops-agent-cluster \
    --service-name devops-agent-service \
    --task-definition devops-agent \
    --desired-count 2 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-xxx],assignPublicIp=ENABLED}" \
    --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:eu-west-1:xxx:targetgroup/xxx,containerName=devops-agent,containerPort=8080"
```

#### Step 6: Create Application Load Balancer

```bash
# Create ALB
aws elbv2 create-load-balancer \
    --name devops-agent-alb \
    --subnets subnet-xxx subnet-yyy \
    --security-groups sg-xxx \
    --region eu-west-1

# Create target group
aws elbv2 create-target-group \
    --name devops-agent-tg \
    --protocol HTTP \
    --port 8080 \
    --vpc-id vpc-xxx \
    --target-type ip \
    --health-check-path /actuator/health

# Create listener
aws elbv2 create-listener \
    --load-balancer-arn arn:aws:elasticloadbalancing:eu-west-1:xxx:loadbalancer/app/xxx \
    --protocol HTTP \
    --port 80 \
    --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:eu-west-1:xxx:targetgroup/xxx
```

### Option 2: AWS EC2

#### Step 1: Launch EC2 Instance

```bash
# Launch instance
aws ec2 run-instances \
    --image-id ami-xxx \
    --instance-type t3.medium \
    --key-name your-key \
    --security-group-ids sg-xxx \
    --subnet-id subnet-xxx \
    --iam-instance-profile Name=devops-agent-role \
    --user-data file://user-data.sh
```

#### Step 2: User Data Script

Create `user-data.sh`:

```bash
#!/bin/bash
set -e

# Update system
yum update -y

# Install Java 17
amazon-linux-extras install java-openjdk17 -y

# Create application directory
mkdir -p /opt/devops-agent
cd /opt/devops-agent

# Download application JAR (from S3 or build server)
aws s3 cp s3://your-bucket/devops-agent-1.0.0.jar app.jar

# Create systemd service
cat > /etc/systemd/system/devops-agent.service << 'EOF'
[Unit]
Description=DevOps Agent
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/devops-agent
ExecStart=/usr/bin/java -jar /opt/devops-agent/app.jar
Restart=always
RestartSec=10
Environment="AWS_REGION=eu-west-1"
Environment="SPRING_PROFILES_ACTIVE=prod"

[Install]
WantedBy=multi-user.target
EOF

# Start service
systemctl daemon-reload
systemctl enable devops-agent
systemctl start devops-agent
```

### Option 3: AWS Elastic Beanstalk

#### Step 1: Create Application Package

```bash
# Build JAR
./gradlew clean build

# Create Procfile
echo "web: java -jar build/libs/devops-agent-1.0.0.jar" > Procfile

# Create .ebextensions for configuration
mkdir -p .ebextensions
```

Create `.ebextensions/environment.config`:

```yaml
option_settings:
  aws:elasticbeanstalk:application:environment:
    AWS_REGION: eu-west-1
    SPRING_PROFILES_ACTIVE: prod
  aws:elasticbeanstalk:environment:proxy:
    ProxyServer: nginx
```

#### Step 2: Deploy

```bash
# Initialize EB
eb init -p java devops-agent --region eu-west-1

# Create environment
eb create devops-agent-prod

# Deploy
eb deploy
```

## Environment Variables

Required environment variables for production:

```bash
# AWS Configuration
AWS_REGION=eu-west-1

# Application
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# DynamoDB
AWS_DYNAMODB_TABLE_NAME=devops-projects

# Secrets Manager
AWS_SECRETS_MANAGER_PREFIX=devops-agent/projects/

# Cache
SPRING_CACHE_TYPE=caffeine
```

## Monitoring Setup

### CloudWatch Logs

```bash
# Create log group
aws logs create-log-group --log-group-name /ecs/devops-agent

# Set retention
aws logs put-retention-policy \
    --log-group-name /ecs/devops-agent \
    --retention-in-days 30
```

### CloudWatch Alarms

```bash
# CPU utilization alarm
aws cloudwatch put-metric-alarm \
    --alarm-name devops-agent-high-cpu \
    --alarm-description "Alert when CPU exceeds 80%" \
    --metric-name CPUUtilization \
    --namespace AWS/ECS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --evaluation-periods 2

# Memory utilization alarm
aws cloudwatch put-metric-alarm \
    --alarm-name devops-agent-high-memory \
    --alarm-description "Alert when memory exceeds 80%" \
    --metric-name MemoryUtilization \
    --namespace AWS/ECS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --evaluation-periods 2
```

## CI/CD Pipeline

### GitHub Actions Workflow

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to AWS

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 17
      uses: actions/setup-java@v2
      with:
        java-version: '17'
        distribution: 'adopt'
    
    - name: Build with Gradle
      run: ./gradlew clean build
    
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v1
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: eu-west-1
    
    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1
    
    - name: Build and push Docker image
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: devops-agent
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
    
    - name: Deploy to ECS
      run: |
        aws ecs update-service \
          --cluster devops-agent-cluster \
          --service devops-agent-service \
          --force-new-deployment
```

## Post-Deployment

### Initialize Database

```bash
curl -X POST https://your-domain.com/api/admin/system/init-db
```

### Health Check

```bash
curl https://your-domain.com/actuator/health
```

### Create First Project

```bash
curl -X POST https://your-domain.com/api/admin/projects/upload \
  -H "Content-Type: application/json" \
  -d @project-config.json
```

## Backup and Recovery

### DynamoDB Backups

```bash
# Enable point-in-time recovery
aws dynamodb update-continuous-backups \
    --table-name devops-projects \
    --point-in-time-recovery-configuration PointInTimeRecoveryEnabled=true

# Create on-demand backup
aws dynamodb create-backup \
    --table-name devops-projects \
    --backup-name devops-projects-backup-$(date +%Y%m%d)
```

### Secrets Manager Backup

Secrets Manager automatically manages versioning. To export:

```bash
# List secrets
aws secretsmanager list-secrets

# Get secret value (for backup)
aws secretsmanager get-secret-value \
    --secret-id devops-agent/projects/PROJECT_ID \
    --query SecretString \
    --output text > backup-PROJECT_ID.json
```

## Troubleshooting

### View ECS Logs

```bash
aws logs tail /ecs/devops-agent --follow
```

### Check Service Status

```bash
aws ecs describe-services \
    --cluster devops-agent-cluster \
    --services devops-agent-service
```

### Update Service

```bash
aws ecs update-service \
    --cluster devops-agent-cluster \
    --service devops-agent-service \
    --force-new-deployment
```

## Cost Optimization

1. Use **Fargate Spot** for non-production environments
2. Enable **DynamoDB on-demand** pricing
3. Set **CloudWatch Logs retention** to 7-30 days
4. Use **ALB with path-based routing** to share with other services
5. Enable **ECS Auto Scaling** based on CPU/memory

## Security Checklist

- ✅ Enable encryption at rest for DynamoDB
- ✅ Use AWS IAM roles (no hardcoded credentials)
- ✅ Enable VPC Flow Logs
- ✅ Use security groups to restrict access
- ✅ Enable ALB access logs
- ✅ Use AWS WAF for API protection
- ✅ Enable CloudTrail for audit logging
- ✅ Use HTTPS with ACM certificates
- ✅ Implement API authentication (API Gateway + Cognito)

