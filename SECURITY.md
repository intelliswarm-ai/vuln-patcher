# Security Policy

## Supported Versions

We release patches for security vulnerabilities. Which versions are eligible for receiving such patches depends on the CVSS v3.0 Rating:

| Version | Supported          | Status |
| ------- | ------------------ | ------ |
| 1.0.x   | :white_check_mark: | Active development |
| < 1.0   | :x:                | Pre-release, not supported |

## Reporting a Vulnerability

The VulnPatcher team takes security seriously. We appreciate your efforts to responsibly disclose your findings, and will make every effort to acknowledge your contributions.

### Where to Report

**Please DO NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:
- Primary: security@intelliswarm.ai
- Backup: vulnpatcher-security@intelliswarm.ai

### What to Include

Please include the following information in your report:

1. **Type of issue** (e.g., buffer overflow, SQL injection, cross-site scripting, etc.)
2. **Full paths of source file(s)** related to the manifestation of the issue
3. **The location of the affected source code** (tag/branch/commit or direct URL)
4. **Any special configuration required** to reproduce the issue
5. **Step-by-step instructions** to reproduce the issue
6. **Proof-of-concept or exploit code** (if possible)
7. **Impact of the issue**, including how an attacker might exploit it
8. **Your contact information** for follow-up questions

### PGP Key

If you wish to encrypt your report, you can use our PGP key:

```
-----BEGIN PGP PUBLIC KEY BLOCK-----
[PGP Key would be inserted here in a real implementation]
-----END PGP PUBLIC KEY BLOCK-----
```

## Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 5 business days
- **Resolution Timeline**: Depends on severity (see below)

### Severity Levels and Response

| Severity | CVSS Score | Response Time | Example |
|----------|------------|---------------|---------|
| Critical | 9.0-10.0 | 24 hours | RCE, Authentication bypass |
| High | 7.0-8.9 | 7 days | Privilege escalation, Data exposure |
| Medium | 4.0-6.9 | 30 days | XSS, CSRF |
| Low | 0.1-3.9 | 90 days | Minor information disclosure |

## Disclosure Policy

- We will confirm receipt of your vulnerability report
- We will confirm the vulnerability and determine its impact
- We will release a fix as soon as possible depending on complexity
- We will credit you (unless you prefer to remain anonymous)

### Coordinated Disclosure

We ask that you:
1. Give us reasonable time to address the issue before public disclosure
2. Avoid exploiting the vulnerability beyond what's necessary for verification
3. Not share the vulnerability with others until it's been resolved

## Security Best Practices for Users

### 1. Keep VulnPatcher Updated

Always run the latest version:
```bash
# Check your version
mvn help:evaluate -Dexpression=project.version -q -DforceStdout

# Update to latest
git pull origin main
mvn clean install
```

### 2. Secure Your Configuration

#### API Keys and Tokens

- Never commit credentials to version control
- Use environment variables or secret management systems
- Rotate credentials regularly

```bash
# Good - Using environment variables
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx

# Bad - Hardcoding in properties
vulnpatcher.github.token=ghp_xxxxxxxxxxxx
```

#### Network Security

- Use HTTPS for all external communications
- Implement proper firewall rules
- Use VPN for sensitive environments

### 3. Access Control

#### API Authentication

Always enable API key authentication in production:

```properties
vulnpatcher.security.api-key-required=true
vulnpatcher.security.api-key-header=X-API-Key
```

#### Rate Limiting

Configure rate limiting to prevent abuse:

```properties
vulnpatcher.security.rate-limit.enabled=true
vulnpatcher.security.rate-limit.requests-per-hour=100
```

### 4. Monitoring and Auditing

#### Enable Security Logging

```properties
quarkus.log.category."ai.intelliswarm.vulnpatcher.security".level=INFO
vulnpatcher.security.audit.enabled=true
vulnpatcher.security.audit.log-file=/var/log/vulnpatcher/audit.log
```

#### Monitor for Suspicious Activity

- Failed authentication attempts
- Unusual API usage patterns
- Unexpected error rates

### 5. Container Security

If using Docker:

```dockerfile
# Run as non-root user
RUN addgroup -g 1001 -S vulnpatcher && \
    adduser -u 1001 -S vulnpatcher -G vulnpatcher
USER vulnpatcher

# Use minimal base images
FROM eclipse-temurin:17-jre-alpine

# Scan images for vulnerabilities
# docker scan vulnpatcher:latest
```

### 6. Database Security

- Use encrypted connections
- Implement least privilege access
- Regular backups with encryption

```properties
# PostgreSQL with SSL
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/vulnpatcher?ssl=true&sslmode=require
```

## Security Features of VulnPatcher

### 1. Input Validation

All user inputs are validated:
- Repository URLs are sanitized
- File paths are checked for directory traversal
- API inputs are validated against schemas

### 2. Dependency Scanning

VulnPatcher uses dependency scanning on itself:
```bash
mvn dependency-check:check
```

### 3. Secure Communication

- All external API calls use HTTPS
- Certificate validation is enforced
- No sensitive data in URLs

### 4. AI Security

- LLM inputs are sanitized
- Prompt injection protection
- Output validation before code execution

## Known Security Considerations

### 1. AI Model Security

- Models run locally via Ollama
- No data sent to external AI services
- Model outputs are validated before use

### 2. Code Execution

- VulnPatcher analyzes but doesn't execute scanned code
- Generated fixes are proposals, not automatic changes
- Sandbox environments recommended for testing

### 3. Third-Party Dependencies

We regularly update dependencies and monitor for vulnerabilities:

```bash
# Check for outdated dependencies
mvn versions:display-dependency-updates

# Check for vulnerable dependencies
mvn dependency-check:check
```

## Security Checklist for Deployment

- [ ] All credentials in environment variables or secrets manager
- [ ] API authentication enabled
- [ ] HTTPS/TLS configured
- [ ] Firewall rules configured
- [ ] Logging and monitoring enabled
- [ ] Regular security updates scheduled
- [ ] Backup and recovery plan in place
- [ ] Incident response plan documented

## Contact

For any security concerns or questions:
- Email: security@intelliswarm.ai
- Response time: Within 48 hours

## Acknowledgments

We thank the following researchers for responsibly disclosing security issues:

| Researcher | Issue | Date |
|------------|-------|------|
| [Will be updated as vulnerabilities are reported and fixed] | | |

---

Last Updated: January 2024
Version: 1.0.0