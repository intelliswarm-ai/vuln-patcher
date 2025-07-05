# VulnPatcher Configuration Guide

## Table of Contents

1. [Configuration Overview](#configuration-overview)
2. [Environment Variables](#environment-variables)
3. [Application Properties](#application-properties)
4. [AI/LLM Configuration](#aillm-configuration)
5. [Git Provider Configuration](#git-provider-configuration)
6. [Vulnerability Sources](#vulnerability-sources)
7. [Performance Tuning](#performance-tuning)
8. [Security Configuration](#security-configuration)
9. [Monitoring & Metrics](#monitoring--metrics)
10. [Advanced Configuration](#advanced-configuration)

## Configuration Overview

VulnPatcher uses Quarkus configuration with support for:
- `application.properties` files
- Environment variables
- System properties
- Configuration profiles (dev, test, prod)
- MicroProfile Config sources

### Configuration Hierarchy

1. System properties (highest priority)
2. Environment variables
3. `.env` file
4. `application.properties`
5. Default values (lowest priority)

### Configuration Profiles

```bash
# Development
mvn quarkus:dev

# Testing
mvn test -Dquarkus.profile=test

# Production
java -Dquarkus.profile=prod -jar target/quarkus-app/quarkus-run.jar
```

## Environment Variables

### Required Variables

```bash
# Git Provider Tokens (at least one required)
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx
BITBUCKET_USERNAME=your-username
BITBUCKET_APP_PASSWORD=app-password

# Vulnerability Database Keys
CVE_API_KEY=your-cve-api-key
SNYK_TOKEN=your-snyk-token

# Ollama Configuration
OLLAMA_BASE_URL=http://localhost:11434
```

### Optional Variables

```bash
# API Security
VULNPATCHER_API_KEY=your-secure-api-key

# Database (if using external DB)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=vulnpatcher
DB_USER=vulnpatcher
DB_PASSWORD=secure-password

# Redis Cache
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis-password

# Performance
JAVA_OPTS="-Xmx4g -Xms2g"
```

### Environment File (.env)

Create `.env` file in project root:

```bash
# Development Environment
QUARKUS_PROFILE=dev

# Git Providers
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx
BITBUCKET_USERNAME=your-username
BITBUCKET_APP_PASSWORD=your-app-password
BITBUCKET_WORKSPACE=your-workspace

# Vulnerability Databases
CVE_API_KEY=your-cve-api-key
SNYK_TOKEN=your-snyk-token
OSS_INDEX_USERNAME=your-username
OSS_INDEX_TOKEN=your-token

# AI/LLM
OLLAMA_BASE_URL=http://localhost:11434

# Monitoring
PROMETHEUS_PUSHGATEWAY_URL=http://localhost:9091
GRAFANA_API_KEY=your-grafana-key

# Notifications (optional)
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/xxx
EMAIL_SMTP_HOST=smtp.gmail.com
EMAIL_SMTP_PORT=587
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=app-specific-password
```

## Application Properties

### Basic Configuration

```properties
# Application Info
quarkus.application.name=vuln-patcher
quarkus.application.version=1.0.0

# HTTP Configuration
quarkus.http.port=8080
quarkus.http.host=0.0.0.0
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with,x-api-key
quarkus.http.cors.exposed-headers=location,link
quarkus.http.cors.access-control-max-age=24H

# SSL/TLS (Production)
%prod.quarkus.http.ssl-port=8443
%prod.quarkus.http.ssl.certificate.files=/certs/tls.crt
%prod.quarkus.http.ssl.certificate.key-files=/certs/tls.key
%prod.quarkus.http.insecure-requests=redirect

# Request Limits
quarkus.http.limits.max-body-size=50M
quarkus.http.limits.max-header-size=20K
quarkus.http.idle-timeout=30M
quarkus.http.read-timeout=30M

# Compression
quarkus.http.enable-compression=true
quarkus.http.compression-level=3
```

### Database Configuration

```properties
# Development - H2 in-memory
%dev.quarkus.datasource.db-kind=h2
%dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:vulnpatcher;DB_CLOSE_DELAY=-1
%dev.quarkus.hibernate-orm.database.generation=drop-and-create

# Test - H2 in-memory
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
%test.quarkus.hibernate-orm.database.generation=drop-and-create

# Production - PostgreSQL
%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.username=${DB_USER:vulnpatcher}
%prod.quarkus.datasource.password=${DB_PASSWORD}
%prod.quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:vulnpatcher}
%prod.quarkus.hibernate-orm.database.generation=validate

# Connection Pool
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=30
quarkus.datasource.jdbc.idle-removal-interval=5M
quarkus.datasource.jdbc.max-lifetime=1H

# Hibernate Settings
quarkus.hibernate-orm.log.sql=%dev.true
quarkus.hibernate-orm.log.format-sql=%dev.true
quarkus.hibernate-orm.statistics=%dev.true

# Flyway Migration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.baseline-version=1.0.0
quarkus.flyway.locations=classpath:db/migration
```

### Caching Configuration

```properties
# Redis Cache
quarkus.redis.hosts=redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}
quarkus.redis.password=${REDIS_PASSWORD:}
quarkus.redis.timeout=30s
quarkus.redis.max-pool-size=20
quarkus.redis.max-pool-waiting=100

# Cache Configuration
quarkus.cache.type=redis
quarkus.cache.redis.key-type=string
quarkus.cache.redis.value-type=json

# Cache TTL by type
vulnpatcher.cache.vulnerability.ttl=3600s
vulnpatcher.cache.scan-result.ttl=86400s
vulnpatcher.cache.ai-response.ttl=1800s
```

## AI/LLM Configuration

### Ollama Integration

```properties
# Ollama Base Configuration
vulnpatcher.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
vulnpatcher.ai.ollama.timeout=300s
vulnpatcher.ai.ollama.max-retries=3

# Model Selection
vulnpatcher.ai.ollama.model.code=deepseek-coder:33b
vulnpatcher.ai.ollama.model.analysis=mixtral:8x7b
vulnpatcher.ai.ollama.model.review=codellama:34b

# Model Parameters
vulnpatcher.ai.ollama.temperature.code=0.2
vulnpatcher.ai.ollama.temperature.analysis=0.3
vulnpatcher.ai.ollama.temperature.review=0.1

vulnpatcher.ai.ollama.max-tokens=4000
vulnpatcher.ai.ollama.top-p=0.95
vulnpatcher.ai.ollama.top-k=40

# Context Management
vulnpatcher.ai.context.chunk-size=1500
vulnpatcher.ai.context.chunk-overlap=200
vulnpatcher.ai.context.max-contexts=100
vulnpatcher.ai.context.embedding-model=all-minilm-l6-v2

# Agent Configuration
vulnpatcher.ai.agents.security-engineer.enabled=true
vulnpatcher.ai.agents.security-engineer.model=deepseek-coder:33b
vulnpatcher.ai.agents.security-engineer.confidence-threshold=0.8

vulnpatcher.ai.agents.sec-lead-reviewer.enabled=true
vulnpatcher.ai.agents.sec-lead-reviewer.model=codellama:34b
vulnpatcher.ai.agents.sec-lead-reviewer.confidence-threshold=0.85

vulnpatcher.ai.agents.security-expert.enabled=true
vulnpatcher.ai.agents.security-expert.model=mixtral:8x7b
vulnpatcher.ai.agents.security-expert.confidence-threshold=0.9
```

### LangChain4j Configuration

```properties
# LangChain4j Settings
quarkus.langchain4j.ollama.base-url=${vulnpatcher.ai.ollama.base-url}
quarkus.langchain4j.ollama.timeout=${vulnpatcher.ai.ollama.timeout}
quarkus.langchain4j.ollama.log-requests=true
quarkus.langchain4j.ollama.log-responses=true

# Embedding Store
quarkus.langchain4j.embedding-store.type=in-memory
quarkus.langchain4j.embedding-store.dimension=384

# Production Embedding Store (Chroma/Pinecone)
%prod.quarkus.langchain4j.embedding-store.type=chroma
%prod.quarkus.langchain4j.chroma.url=http://chroma:8000
%prod.quarkus.langchain4j.chroma.collection-name=vulnpatcher
```

## Git Provider Configuration

### GitHub

```properties
# GitHub Configuration
vulnpatcher.github.enabled=true
vulnpatcher.github.token=${GITHUB_TOKEN}
vulnpatcher.github.api-url=https://api.github.com
vulnpatcher.github.api-version=2022-11-28
vulnpatcher.github.rate-limit.enabled=true
vulnpatcher.github.rate-limit.max-retries=3
vulnpatcher.github.timeout=60s

# GitHub Enterprise
%prod.vulnpatcher.github.api-url=https://github.enterprise.com/api/v3
```

### GitLab

```properties
# GitLab Configuration
vulnpatcher.gitlab.enabled=true
vulnpatcher.gitlab.token=${GITLAB_TOKEN}
vulnpatcher.gitlab.url=https://gitlab.com
vulnpatcher.gitlab.api-version=4
vulnpatcher.gitlab.timeout=60s

# GitLab Self-Hosted
%prod.vulnpatcher.gitlab.url=https://gitlab.company.com
```

### Bitbucket

```properties
# Bitbucket Configuration
vulnpatcher.bitbucket.enabled=true
vulnpatcher.bitbucket.username=${BITBUCKET_USERNAME}
vulnpatcher.bitbucket.app-password=${BITBUCKET_APP_PASSWORD}
vulnpatcher.bitbucket.workspace=${BITBUCKET_WORKSPACE}
vulnpatcher.bitbucket.api-url=https://api.bitbucket.org/2.0
vulnpatcher.bitbucket.timeout=60s

# Bitbucket Server (Self-Hosted)
%prod.vulnpatcher.bitbucket.api-url=https://bitbucket.company.com/rest/api/1.0
```

## Vulnerability Sources

### CVE/NVD Configuration

```properties
# CVE Database
vulnpatcher.sources.cve.enabled=true
vulnpatcher.sources.cve.api-key=${CVE_API_KEY}
vulnpatcher.sources.cve.base-url=https://services.nvd.nist.gov/rest/json/cves/2.0
vulnpatcher.sources.cve.rate-limit=50/30s
vulnpatcher.sources.cve.page-size=100
vulnpatcher.sources.cve.update-interval=3600s
vulnpatcher.sources.cve.cache-enabled=true
```

### GitHub Security Advisories (GHSA)

```properties
# GHSA Configuration
vulnpatcher.sources.ghsa.enabled=true
vulnpatcher.sources.ghsa.token=${GITHUB_TOKEN}
vulnpatcher.sources.ghsa.graphql-url=https://api.github.com/graphql
vulnpatcher.sources.ghsa.update-interval=1800s
```

### OSV (Open Source Vulnerabilities)

```properties
# OSV Configuration
vulnpatcher.sources.osv.enabled=true
vulnpatcher.sources.osv.base-url=https://api.osv.dev
vulnpatcher.sources.osv.batch-size=100
vulnpatcher.sources.osv.ecosystems=Maven,npm,PyPI,Go,Packagist,RubyGems
```

### Snyk

```properties
# Snyk Configuration
vulnpatcher.sources.snyk.enabled=true
vulnpatcher.sources.snyk.token=${SNYK_TOKEN}
vulnpatcher.sources.snyk.api-url=https://api.snyk.io/v1
vulnpatcher.sources.snyk.org-id=${SNYK_ORG_ID}
```

### OSS Index

```properties
# OSS Index Configuration
vulnpatcher.sources.oss-index.enabled=true
vulnpatcher.sources.oss-index.username=${OSS_INDEX_USERNAME}
vulnpatcher.sources.oss-index.token=${OSS_INDEX_TOKEN}
vulnpatcher.sources.oss-index.base-url=https://ossindex.sonatype.org/api/v3
```

### OVAL

```properties
# OVAL Configuration
vulnpatcher.sources.oval.enabled=true
vulnpatcher.sources.oval.feeds=redhat,ubuntu,debian,suse
vulnpatcher.sources.oval.update-interval=86400s
```

## Performance Tuning

### Scanning Performance

```properties
# Parallel Processing
vulnpatcher.scanning.parallel-files=10
vulnpatcher.scanning.parallel-repos=3
vulnpatcher.scanning.max-file-size=10MB
vulnpatcher.scanning.timeout=3600s

# File Filtering
vulnpatcher.scanning.included-extensions=.java,.py,.js,.ts,.go,.rb,.php,.cs,.cpp,.c,.h
vulnpatcher.scanning.excluded-dirs=node_modules,vendor,.git,build,dist,target,bin,obj
vulnpatcher.scanning.excluded-files=*.min.js,*.min.css,*.map

# Memory Management
vulnpatcher.scanning.stream-threshold=1MB
vulnpatcher.scanning.buffer-size=8192
```

### Thread Pool Configuration

```properties
# Quarkus Thread Pools
quarkus.thread-pool.core-threads=20
quarkus.thread-pool.max-threads=100
quarkus.thread-pool.queue-size=1000
quarkus.thread-pool.growth-resistance=0.5
quarkus.thread-pool.shutdown-timeout=30

# Vert.x Configuration
quarkus.vertx.worker-pool-size=40
quarkus.vertx.event-bus.connect-timeout=60000
quarkus.vertx.max-event-loop-execute-time=30s
quarkus.vertx.warning-exception-time=20s
```

### JVM Tuning

```properties
# JVM Arguments (set via JAVA_OPTS or in container)
quarkus.native.additional-build-args=\
  -H:ResourceConfigurationFiles=resources-config.json,\
  -H:ReflectionConfigurationFiles=reflection-config.json

# Memory Settings
quarkus.jvm.args=-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled
```

## Security Configuration

### API Security

```properties
# API Key Authentication
vulnpatcher.security.api-key-required=true
vulnpatcher.security.api-key-header=X-API-Key
vulnpatcher.security.api-keys=${VULNPATCHER_API_KEYS}

# Rate Limiting
vulnpatcher.security.rate-limit.enabled=true
vulnpatcher.security.rate-limit.requests-per-hour=100
vulnpatcher.security.rate-limit.burst-size=20

# IP Whitelisting
vulnpatcher.security.ip-whitelist.enabled=false
vulnpatcher.security.ip-whitelist.addresses=127.0.0.1,10.0.0.0/8

# CORS
vulnpatcher.security.cors.allowed-origins=https://app.example.com
vulnpatcher.security.cors.allowed-methods=GET,POST,PUT,DELETE
vulnpatcher.security.cors.max-age=86400
```

### Encryption

```properties
# TLS Configuration
quarkus.http.ssl.protocols=TLSv1.3,TLSv1.2
quarkus.http.ssl.cipher-suites=TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256

# Secrets Encryption
vulnpatcher.security.encryption.enabled=true
vulnpatcher.security.encryption.algorithm=AES/GCM/NoPadding
vulnpatcher.security.encryption.key-source=vault
vulnpatcher.security.vault.url=${VAULT_URL}
vulnpatcher.security.vault.token=${VAULT_TOKEN}
```

## Monitoring & Metrics

### Metrics Configuration

```properties
# Micrometer Metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.registry-enabled=true
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true
quarkus.micrometer.binder.http-server=true
quarkus.micrometer.binder.http-client=true

# Prometheus Export
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/metrics

# Custom Metrics
vulnpatcher.metrics.detailed=true
vulnpatcher.metrics.histogram.enabled=true
vulnpatcher.metrics.histogram.percentiles=0.5,0.95,0.99
```

### Health Checks

```properties
# Health Check Configuration
quarkus.health.openapi.included=true
quarkus.smallrye-health.root-path=/health
quarkus.smallrye-health.liveness-path=/live
quarkus.smallrye-health.readiness-path=/ready
quarkus.smallrye-health.startup-path=/started

# Custom Health Checks
vulnpatcher.health.ollama.enabled=true
vulnpatcher.health.ollama.timeout=5s
vulnpatcher.health.database.enabled=true
vulnpatcher.health.git-providers.enabled=true
```

### Logging

```properties
# Console Logging
quarkus.log.console.enable=true
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
quarkus.log.console.level=INFO
quarkus.log.console.color=true

# File Logging
quarkus.log.file.enable=true
quarkus.log.file.path=logs/vulnpatcher.log
quarkus.log.file.level=INFO
quarkus.log.file.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n
quarkus.log.file.rotation.max-file-size=10M
quarkus.log.file.rotation.max-backup-index=10

# Category Specific Logging
quarkus.log.category."ai.intelliswarm.vulnpatcher".level=DEBUG
quarkus.log.category."dev.langchain4j".level=INFO
quarkus.log.category."org.hibernate".level=WARN
quarkus.log.category."io.quarkus".level=INFO

# JSON Logging (Production)
%prod.quarkus.log.console.json=true
%prod.quarkus.log.console.json.pretty-print=false
%prod.quarkus.log.console.json.exception-output-type=detailed
%prod.quarkus.log.console.json.print-details=true
```

### Distributed Tracing

```properties
# OpenTelemetry
quarkus.opentelemetry.enabled=true
quarkus.opentelemetry.tracer.exporter.otlp.endpoint=http://localhost:4317
quarkus.opentelemetry.tracer.exporter.otlp.headers=Authorization=Bearer ${OTLP_TOKEN}
quarkus.opentelemetry.tracer.resource-attributes=service.name=vulnpatcher,service.version=1.0.0
```

## Advanced Configuration

### Scheduler Configuration

```properties
# Scheduled Tasks
vulnpatcher.scheduler.enabled=true
vulnpatcher.scheduler.thread-pool-size=5

# Vulnerability Database Updates
vulnpatcher.scheduler.vulnerability-update.enabled=true
vulnpatcher.scheduler.vulnerability-update.cron=0 0 */6 * * ?
vulnpatcher.scheduler.vulnerability-update.initial-delay=5m

# Repository Scanning
vulnpatcher.scheduler.repo-scan.enabled=false
vulnpatcher.scheduler.repo-scan.cron=0 0 2 * * ?
vulnpatcher.scheduler.repo-scan.repositories=${SCHEDULED_REPOS}
```

### Notification Configuration

```properties
# Email Notifications
vulnpatcher.notifications.email.enabled=true
vulnpatcher.notifications.email.smtp-host=${EMAIL_SMTP_HOST}
vulnpatcher.notifications.email.smtp-port=${EMAIL_SMTP_PORT}
vulnpatcher.notifications.email.username=${EMAIL_USERNAME}
vulnpatcher.notifications.email.password=${EMAIL_PASSWORD}
vulnpatcher.notifications.email.from=vulnpatcher@example.com
vulnpatcher.notifications.email.to=${NOTIFICATION_EMAILS}

# Slack Notifications
vulnpatcher.notifications.slack.enabled=true
vulnpatcher.notifications.slack.webhook-url=${SLACK_WEBHOOK_URL}
vulnpatcher.notifications.slack.channel=#security-alerts
vulnpatcher.notifications.slack.username=VulnPatcher

# Notification Triggers
vulnpatcher.notifications.triggers.critical-vulnerability=true
vulnpatcher.notifications.triggers.scan-complete=true
vulnpatcher.notifications.triggers.fix-generated=true
vulnpatcher.notifications.triggers.pr-created=true
```

### Feature Flags

```properties
# Feature Toggle Configuration
vulnpatcher.features.auto-fix=true
vulnpatcher.features.auto-pr=false
vulnpatcher.features.incremental-scan=true
vulnpatcher.features.experimental-matchers=false
vulnpatcher.features.ai-confidence-threshold=0.8

# A/B Testing
vulnpatcher.features.new-ui.enabled=false
vulnpatcher.features.new-ui.percentage=10
```

### Custom Configuration

```properties
# Organization Specific
vulnpatcher.org.name=Your Organization
vulnpatcher.org.security-team-email=security@example.com
vulnpatcher.org.compliance-mode=SOC2

# Custom Rules
vulnpatcher.rules.custom.enabled=true
vulnpatcher.rules.custom.path=config/custom-rules.json
vulnpatcher.rules.severity-overrides.enabled=true
vulnpatcher.rules.severity-overrides.path=config/severity-overrides.json

# Integration Points
vulnpatcher.integrations.jira.enabled=true
vulnpatcher.integrations.jira.url=${JIRA_URL}
vulnpatcher.integrations.jira.username=${JIRA_USERNAME}
vulnpatcher.integrations.jira.token=${JIRA_TOKEN}
vulnpatcher.integrations.jira.project-key=SEC
```

## Configuration Best Practices

### 1. Environment-Specific Configuration

```properties
# Use profiles for different environments
%dev.quarkus.log.level=DEBUG
%test.quarkus.log.level=INFO
%prod.quarkus.log.level=WARN

# Override with environment variables in production
vulnpatcher.github.token=${GITHUB_TOKEN:default-dev-token}
```

### 2. Secrets Management

```bash
# Never commit secrets
echo "application-local.properties" >> .gitignore

# Use external secret management
kubectl create secret generic vulnpatcher-secrets \
  --from-env-file=.env.production
```

### 3. Configuration Validation

```java
@ConfigMapping(prefix = "vulnpatcher")
@Validated
public interface VulnPatcherConfig {
    
    @NotBlank
    String apiKey();
    
    @Min(1)
    @Max(100)
    int parallelScans();
    
    @Valid
    OllamaConfig ollama();
}
```

### 4. Dynamic Configuration

```properties
# Use Consul or etcd for dynamic config
quarkus.config.source.consul.enabled=true
quarkus.config.source.consul.host=consul.service.consul
quarkus.config.source.consul.prefix=config/vulnpatcher
```

---

Last Updated: January 2024
Version: 1.0.0