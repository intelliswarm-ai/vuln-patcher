# VulnPatcher User Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [System Requirements](#system-requirements)
4. [Installation Guide](#installation-guide)
5. [Configuration](#configuration)
6. [Basic Usage](#basic-usage)
7. [Advanced Features](#advanced-features)
8. [API Reference](#api-reference)
9. [Troubleshooting](#troubleshooting)
10. [Best Practices](#best-practices)

## Introduction

VulnPatcher is an enterprise-grade, AI-powered vulnerability detection and automated patching system that helps organizations identify and fix security vulnerabilities in their codebases automatically. This guide will walk you through everything you need to know to effectively use VulnPatcher.

### Key Benefits

- **Automated Security**: Continuous scanning and patching of vulnerabilities
- **Multi-Language Support**: Works with Java, Python, C++, Kotlin, JavaScript, Angular, React, and SQL
- **AI-Powered Fixes**: Intelligent fix generation that maintains code functionality
- **Multi-Platform**: Supports GitHub, GitLab, and Bitbucket
- **Enterprise Ready**: Built for scale with reactive architecture

## Getting Started

### Quick Start

1. **Install Prerequisites**
   ```bash
   # Install Java 17+
   # Install Maven 3.8+
   # Install Ollama
   curl -fsSL https://ollama.ai/install.sh | sh
   ```

2. **Pull Required AI Models**
   ```bash
   ollama pull deepseek-coder:33b
   ollama pull mixtral:8x7b
   ollama pull codellama:34b
   ```

3. **Clone and Build**
   ```bash
   git clone https://github.com/intelliswarm/vuln-patcher.git
   cd vuln-patcher
   mvn clean install
   ```

4. **Configure Credentials**
   ```bash
   export GITHUB_TOKEN=your_github_token
   export GITLAB_TOKEN=your_gitlab_token
   # Add other credentials as needed
   ```

5. **Run VulnPatcher**
   ```bash
   mvn quarkus:dev
   ```

## System Requirements

### Hardware Requirements

- **CPU**: 8+ cores recommended (for AI model processing)
- **RAM**: 16GB minimum, 32GB recommended
- **Storage**: 50GB+ free space (for models and analysis)
- **Network**: Stable internet connection for vulnerability database access

### Software Requirements

- **Operating System**: Linux, macOS, or Windows with WSL2
- **Java**: JDK 17 or higher
- **Maven**: 3.8 or higher
- **Ollama**: Latest version
- **Git**: 2.x or higher
- **Docker**: (Optional) For containerized deployment

## Installation Guide

### Local Installation

1. **Install Java 17+**
   ```bash
   # Ubuntu/Debian
   sudo apt update
   sudo apt install openjdk-17-jdk
   
   # macOS
   brew install openjdk@17
   
   # Verify installation
   java -version
   ```

2. **Install Maven**
   ```bash
   # Ubuntu/Debian
   sudo apt install maven
   
   # macOS
   brew install maven
   
   # Verify installation
   mvn -version
   ```

3. **Install Ollama**
   ```bash
   # Linux/macOS
   curl -fsSL https://ollama.ai/install.sh | sh
   
   # Start Ollama service
   ollama serve
   ```

4. **Download AI Models**
   ```bash
   # Pull all required models
   ollama pull deepseek-coder:33b
   ollama pull mixtral:8x7b
   ollama pull codellama:34b
   
   # Verify models
   ollama list
   ```

### Docker Installation

1. **Build Docker Image**
   ```bash
   docker build -t vuln-patcher:latest .
   ```

2. **Run with Docker Compose**
   ```yaml
   # docker-compose.yml
   version: '3.8'
   services:
     vuln-patcher:
       image: vuln-patcher:latest
       ports:
         - "8080:8080"
       environment:
         - GITHUB_TOKEN=${GITHUB_TOKEN}
         - OLLAMA_BASE_URL=http://ollama:11434
       depends_on:
         - ollama
     
     ollama:
       image: ollama/ollama:latest
       ports:
         - "11434:11434"
       volumes:
         - ollama_data:/root/.ollama
   
   volumes:
     ollama_data:
   ```

3. **Start Services**
   ```bash
   docker-compose up -d
   ```

## Configuration

### Application Configuration

Create or edit `src/main/resources/application.properties`:

```properties
# Ollama Configuration
vulnpatcher.ai.ollama.base-url=http://localhost:11434
vulnpatcher.ai.ollama.timeout=300s
vulnpatcher.ai.ollama.model.code=deepseek-coder:33b
vulnpatcher.ai.ollama.model.analysis=mixtral:8x7b
vulnpatcher.ai.ollama.model.review=codellama:34b

# Context Management
vulnpatcher.context.chunk-size=1500
vulnpatcher.context.chunk-overlap=200
vulnpatcher.context.max-contexts=100

# Scanning Configuration
vulnpatcher.scanning.parallel-files=10
vulnpatcher.scanning.max-file-size=10MB
vulnpatcher.scanning.excluded-dirs=node_modules,vendor,.git,build,dist

# GitHub Configuration
vulnpatcher.github.token=${GITHUB_TOKEN}
vulnpatcher.github.api-url=https://api.github.com
vulnpatcher.github.enabled=true

# GitLab Configuration
vulnpatcher.gitlab.token=${GITLAB_TOKEN}
vulnpatcher.gitlab.url=https://gitlab.com
vulnpatcher.gitlab.enabled=true

# Bitbucket Configuration
vulnpatcher.bitbucket.username=${BITBUCKET_USERNAME}
vulnpatcher.bitbucket.app-password=${BITBUCKET_APP_PASSWORD}
vulnpatcher.bitbucket.workspace=${BITBUCKET_WORKSPACE}
vulnpatcher.bitbucket.enabled=true

# Vulnerability Sources
vulnpatcher.sources.cve.enabled=true
vulnpatcher.sources.cve.api-key=${CVE_API_KEY}
vulnpatcher.sources.cve.base-url=https://services.nvd.nist.gov/rest/json/cves/2.0

vulnpatcher.sources.ghsa.enabled=true
vulnpatcher.sources.ghsa.token=${GITHUB_TOKEN}

vulnpatcher.sources.osv.enabled=true
vulnpatcher.sources.osv.base-url=https://api.osv.dev

vulnpatcher.sources.snyk.enabled=true
vulnpatcher.sources.snyk.token=${SNYK_TOKEN}

# Performance Tuning
vulnpatcher.performance.cache-enabled=true
vulnpatcher.performance.cache-ttl=3600
vulnpatcher.performance.max-concurrent-scans=5
vulnpatcher.performance.scan-timeout=3600

# Security Settings
vulnpatcher.security.api-key-required=true
vulnpatcher.security.rate-limit=100/hour
vulnpatcher.security.allowed-origins=http://localhost:3000
```

### Environment Variables

Create a `.env` file in the project root:

```bash
# Git Provider Credentials
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
export GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx
export BITBUCKET_USERNAME=your-username
export BITBUCKET_APP_PASSWORD=your-app-password
export BITBUCKET_WORKSPACE=your-workspace

# Vulnerability Database API Keys
export CVE_API_KEY=your-cve-api-key
export SNYK_TOKEN=your-snyk-token

# Application Settings
export VULNPATCHER_API_KEY=your-secure-api-key
export OLLAMA_BASE_URL=http://localhost:11434

# Database (if using external DB)
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=vulnpatcher
export DB_USER=vulnpatcher
export DB_PASSWORD=secure-password
```

## Basic Usage

### 1. Scanning a Repository

#### Via REST API

```bash
# Scan a public repository
curl -X POST http://localhost:8080/api/v1/vulnpatcher/scan \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${VULNPATCHER_API_KEY}" \
  -d '{
    "repositoryUrl": "https://github.com/example/my-app",
    "branch": "main",
    "languages": ["java", "javascript"],
    "severityThreshold": "MEDIUM"
  }'
```

#### Via Web UI (if available)

1. Navigate to http://localhost:8080
2. Enter repository URL
3. Select languages to scan
4. Choose severity threshold
5. Click "Start Scan"

### 2. Monitoring Scan Progress

#### Real-time Progress via SSE

```bash
# Stream scan progress
curl -N http://localhost:8080/api/v1/vulnpatcher/scan/{scanId}/stream \
  -H "Accept: text/event-stream" \
  -H "X-API-Key: ${VULNPATCHER_API_KEY}"
```

#### Check Scan Status

```bash
curl http://localhost:8080/api/v1/vulnpatcher/scan/{scanId} \
  -H "X-API-Key: ${VULNPATCHER_API_KEY}"
```

### 3. Reviewing Results

The scan results include:
- Vulnerability ID and severity
- Affected file and line number
- Vulnerability description
- AI-generated fix suggestion
- Confidence score

Example response:
```json
{
  "scanId": "scan-123",
  "status": "completed",
  "vulnerabilitiesFound": 5,
  "vulnerabilities": [
    {
      "id": "CVE-2021-44228",
      "severity": "CRITICAL",
      "file": "pom.xml",
      "lineNumber": 45,
      "description": "Log4j Remote Code Execution",
      "suggestedFix": {
        "description": "Update Log4j to version 2.21.1",
        "confidence": 0.99,
        "patch": "..."
      }
    }
  ]
}
```

### 4. Creating Pull Requests

```bash
# Create PR with all fixes
curl -X POST http://localhost:8080/api/v1/vulnpatcher/scan/{scanId}/create-pr \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${VULNPATCHER_API_KEY}" \
  -d '{
    "title": "Security fixes for detected vulnerabilities",
    "description": "Automated security fixes by VulnPatcher",
    "targetBranch": "main",
    "reviewers": ["security-team"],
    "labels": ["security", "automated"]
  }'
```

## Advanced Features

### 1. Custom Vulnerability Rules

Create custom rules in `config/custom-rules.json`:

```json
{
  "rules": [
    {
      "id": "CUSTOM-001",
      "name": "Hardcoded API Key",
      "pattern": "api[_-]?key\\s*=\\s*[\"'][^\"']+[\"']",
      "languages": ["java", "javascript"],
      "severity": "HIGH",
      "description": "Hardcoded API keys pose security risks",
      "fix": "Use environment variables or secure vaults"
    }
  ]
}
```

### 2. AI Model Tuning

Customize AI behavior in `config/ai-config.json`:

```json
{
  "models": {
    "code-generation": {
      "temperature": 0.2,
      "max_tokens": 2000,
      "system_prompt": "You are an expert security engineer..."
    },
    "code-review": {
      "temperature": 0.1,
      "max_tokens": 1000,
      "system_prompt": "You are a senior code reviewer..."
    }
  }
}
```

### 3. Workflow Automation

#### Scheduled Scans

```properties
# In application.properties
vulnpatcher.scheduler.enabled=true
vulnpatcher.scheduler.cron=0 0 2 * * ?  # Daily at 2 AM
vulnpatcher.scheduler.repositories=repo1,repo2,repo3
```

#### CI/CD Integration

```yaml
# GitHub Actions example
name: Security Scan
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run VulnPatcher Scan
        run: |
          curl -X POST ${{ secrets.VULNPATCHER_URL }}/api/v1/vulnpatcher/scan \
            -H "X-API-Key: ${{ secrets.VULNPATCHER_API_KEY }}" \
            -d '{
              "repositoryUrl": "${{ github.event.repository.clone_url }}",
              "branch": "${{ github.ref_name }}",
              "autoFix": true
            }'
```

### 4. Multi-Repository Management

```bash
# Scan multiple repositories
curl -X POST http://localhost:8080/api/v1/vulnpatcher/batch-scan \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${VULNPATCHER_API_KEY}" \
  -d '{
    "repositories": [
      {
        "url": "https://github.com/org/repo1",
        "branch": "main"
      },
      {
        "url": "https://gitlab.com/org/repo2",
        "branch": "develop"
      }
    ],
    "commonSettings": {
      "languages": ["java", "python"],
      "severityThreshold": "HIGH"
    }
  }'
```

### 5. Custom Fix Strategies

Define custom fix strategies:

```java
// In config/fix-strategies.properties
vulnpatcher.fix.strategy.sql-injection=parameterized-queries
vulnpatcher.fix.strategy.xss=output-encoding
vulnpatcher.fix.strategy.xxe=disable-external-entities
```

## API Reference

### Core Endpoints

#### POST /api/v1/vulnpatcher/scan
Start a new vulnerability scan.

**Request Body:**
```json
{
  "repositoryUrl": "string",
  "branch": "string",
  "languages": ["string"],
  "severityThreshold": "LOW|MEDIUM|HIGH|CRITICAL",
  "credentials": {
    "token": "string",
    "username": "string",
    "password": "string"
  }
}
```

**Response:**
```json
{
  "scanId": "string",
  "status": "pending|in_progress|completed|failed",
  "message": "string"
}
```

#### GET /api/v1/vulnpatcher/scan/{scanId}
Get scan results.

**Response:**
```json
{
  "scanId": "string",
  "status": "string",
  "startTime": "2024-01-15T10:00:00Z",
  "endTime": "2024-01-15T10:05:00Z",
  "repository": "string",
  "branch": "string",
  "vulnerabilitiesFound": 10,
  "vulnerabilities": [...]
}
```

#### POST /api/v1/vulnpatcher/scan/{scanId}/create-pr
Create a pull request with fixes.

**Request Body:**
```json
{
  "title": "string",
  "description": "string",
  "targetBranch": "string",
  "sourceBranch": "string",
  "reviewers": ["string"],
  "labels": ["string"],
  "autoMerge": false
}
```

#### GET /api/v1/vulnpatcher/scan/{scanId}/stream
Stream scan progress via Server-Sent Events.

**Response:** SSE stream
```
event: progress
data: {"percentage": 25, "message": "Scanning file 10 of 40"}

event: vulnerability
data: {"id": "CVE-2023-12345", "severity": "HIGH", "file": "src/main.java"}

event: complete
data: {"vulnerabilitiesFound": 5, "duration": 120}
```

### Workflow Endpoints

#### POST /api/v1/workflows/vulnerability-fix
Start an AI workflow for specific vulnerability.

**Request Body:**
```json
{
  "repositoryUrl": "string",
  "vulnerability": {
    "id": "string",
    "severity": "string"
  },
  "filePath": "string",
  "lineNumber": 42,
  "affectedCode": "string",
  "language": "string",
  "framework": "string"
}
```

#### GET /api/v1/workflows/{workflowId}/events
Stream workflow events.

### Administrative Endpoints

#### GET /health
Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "checks": {
    "ollama": "UP",
    "database": "UP",
    "memory": "UP"
  }
}
```

#### GET /metrics
Prometheus metrics endpoint.

## Troubleshooting

### Common Issues

#### 1. Ollama Connection Failed

**Error:** "Failed to connect to Ollama at http://localhost:11434"

**Solution:**
```bash
# Check if Ollama is running
ps aux | grep ollama

# Start Ollama if not running
ollama serve

# Verify connection
curl http://localhost:11434/api/tags
```

#### 2. Insufficient Memory

**Error:** "OutOfMemoryError" or slow performance

**Solution:**
```bash
# Increase JVM heap size
export JAVA_OPTS="-Xmx8g -Xms4g"

# Or in application.properties
quarkus.jvm.args=-Xmx8g -Xms4g
```

#### 3. Git Authentication Failed

**Error:** "Authentication failed for repository"

**Solution:**
1. Verify token permissions:
   - GitHub: repo, read:org
   - GitLab: api, read_repository
   - Bitbucket: repository:read, pullrequest:write

2. Check token format:
   ```bash
   # GitHub
   export GITHUB_TOKEN=ghp_xxxx
   
   # GitLab  
   export GITLAB_TOKEN=glpat-xxxx
   ```

#### 4. Model Loading Timeout

**Error:** "Timeout loading model deepseek-coder:33b"

**Solution:**
```bash
# Pre-load models
ollama run deepseek-coder:33b "test"
ollama run mixtral:8x7b "test"
ollama run codellama:34b "test"

# Increase timeout
vulnpatcher.ai.ollama.timeout=600s
```

#### 5. Scan Stuck or Slow

**Possible Causes:**
- Large repository
- Complex vulnerabilities
- Network issues

**Solutions:**
```properties
# Adjust performance settings
vulnpatcher.scanning.parallel-files=20
vulnpatcher.scanning.max-file-size=5MB
vulnpatcher.performance.scan-timeout=7200
```

### Debugging

#### Enable Debug Logging

```properties
# In application.properties
quarkus.log.level=INFO
quarkus.log.category."ai.intelliswarm.vulnpatcher".level=DEBUG
quarkus.log.category."dev.langchain4j".level=DEBUG
```

#### View Logs

```bash
# Development mode
mvn quarkus:dev

# Production logs
tail -f logs/vulnpatcher.log

# Filter by component
grep "VulnerabilityDetectionEngine" logs/vulnpatcher.log
```

#### Performance Profiling

```bash
# Enable JFR profiling
java -XX:StartFlightRecording=filename=vulnpatcher.jfr,duration=60s -jar target/quarkus-app/quarkus-run.jar

# Analyze with JDK Mission Control
jmc vulnpatcher.jfr
```

## Best Practices

### 1. Security Configuration

- **API Keys**: Always use strong, unique API keys
- **Network Security**: Use HTTPS in production
- **Token Storage**: Never commit tokens to version control
- **Access Control**: Implement proper RBAC

### 2. Performance Optimization

- **Repository Size**: For repos >1GB, use incremental scanning
- **Concurrent Scans**: Limit based on available resources
- **Caching**: Enable caching for frequently scanned repos
- **Model Selection**: Use smaller models for faster results

### 3. Integration Guidelines

- **CI/CD**: Run scans on PR creation, not every commit
- **Notifications**: Set up alerts for critical vulnerabilities
- **Review Process**: Always review AI-generated fixes
- **Gradual Rollout**: Test fixes in staging first

### 4. Monitoring and Maintenance

- **Metrics**: Monitor scan times, success rates, fix accuracy
- **Logs**: Regularly review logs for errors or anomalies
- **Updates**: Keep vulnerability databases updated
- **Model Updates**: Periodically update AI models

### 5. Customization Tips

- **Language Priority**: Configure based on your tech stack
- **Severity Thresholds**: Adjust based on risk tolerance
- **Fix Strategies**: Customize for your coding standards
- **Exclusions**: Configure to skip test files, vendored code

## Support and Resources

### Getting Help

- **Documentation**: https://docs.intelliswarm.ai/vuln-patcher
- **GitHub Issues**: https://github.com/intelliswarm/vuln-patcher/issues
- **Community Forum**: https://community.intelliswarm.ai
- **Email Support**: support@intelliswarm.ai

### Additional Resources

- [Architecture Guide](./ARCHITECTURE.md)
- [API Documentation](./API_REFERENCE.md)
- [Contributing Guide](../CONTRIBUTING.md)
- [Security Policy](../SECURITY.md)

### Training Materials

- Video Tutorials: https://youtube.com/intelliswarm
- Webinar Series: Monthly security workshops
- Best Practices Blog: https://blog.intelliswarm.ai

---

Last Updated: January 2024
Version: 1.0.0