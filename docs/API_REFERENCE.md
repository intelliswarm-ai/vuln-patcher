# VulnPatcher API Reference

## Overview

The VulnPatcher API provides RESTful endpoints for vulnerability scanning, fix generation, and pull request management. This document covers all available endpoints, request/response formats, and usage examples.

## Base URL

```
http://localhost:8080/api/v1
```

## Authentication

All API requests require authentication via API key:

```bash
curl -H "X-API-Key: your-api-key" http://localhost:8080/api/v1/...
```

## Table of Contents

1. [Scan Management](#scan-management)
2. [Workflow Management](#workflow-management)
3. [Vulnerability Operations](#vulnerability-operations)
4. [Repository Management](#repository-management)
5. [Configuration](#configuration)
6. [Health & Monitoring](#health--monitoring)
7. [WebSocket & SSE](#websocket--sse)
8. [Error Handling](#error-handling)

## Scan Management

### Start New Scan

**POST** `/vulnpatcher/scan`

Initiates a vulnerability scan on a repository.

#### Request

```json
{
  "repositoryUrl": "https://github.com/example/repo",
  "branch": "main",
  "languages": ["java", "python", "javascript"],
  "severityThreshold": "MEDIUM",
  "scanType": "FULL",
  "excludePaths": ["test/", "docs/"],
  "includeDevDependencies": false,
  "credentials": {
    "token": "github_pat_xxx",
    "type": "bearer"
  },
  "options": {
    "autoFix": true,
    "createPR": false,
    "maxVulnerabilities": 100
  }
}
```

#### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| repositoryUrl | string | Yes | Full URL of the repository |
| branch | string | No | Branch to scan (default: main/master) |
| languages | array | No | Languages to scan (default: all) |
| severityThreshold | enum | No | Minimum severity (LOW, MEDIUM, HIGH, CRITICAL) |
| scanType | enum | No | FULL, INCREMENTAL, or QUICK |
| excludePaths | array | No | Paths to exclude from scanning |
| includeDevDependencies | boolean | No | Include dev dependencies (default: false) |
| credentials | object | No | Authentication credentials |
| options | object | No | Additional scan options |

#### Response

```json
{
  "scanId": "scan-7f8a9b3c-2d4e-4f6a-8b9d-1e3f5a7c9d2b",
  "status": "pending",
  "message": "Scan initiated successfully",
  "estimatedDuration": 300,
  "queuePosition": 2,
  "links": {
    "self": "/api/v1/vulnpatcher/scan/scan-7f8a9b3c",
    "stream": "/api/v1/vulnpatcher/scan/scan-7f8a9b3c/stream",
    "cancel": "/api/v1/vulnpatcher/scan/scan-7f8a9b3c/cancel"
  }
}
```

#### Example

```bash
curl -X POST http://localhost:8080/api/v1/vulnpatcher/scan \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "repositoryUrl": "https://github.com/spring-projects/spring-petclinic",
    "branch": "main",
    "languages": ["java"],
    "severityThreshold": "HIGH",
    "options": {
      "autoFix": true
    }
  }'
```

### Get Scan Results

**GET** `/vulnpatcher/scan/{scanId}`

Retrieves detailed results of a vulnerability scan.

#### Response

```json
{
  "scanId": "scan-7f8a9b3c-2d4e-4f6a-8b9d-1e3f5a7c9d2b",
  "status": "completed",
  "repository": {
    "url": "https://github.com/example/repo",
    "branch": "main",
    "commit": "a1b2c3d4e5f6",
    "scannedAt": "2024-01-15T10:00:00Z"
  },
  "summary": {
    "totalFiles": 156,
    "filesScanned": 156,
    "vulnerabilitiesFound": 12,
    "fixesGenerated": 10,
    "scanDuration": 245000,
    "severityBreakdown": {
      "CRITICAL": 2,
      "HIGH": 5,
      "MEDIUM": 3,
      "LOW": 2
    }
  },
  "vulnerabilities": [
    {
      "id": "vuln-001",
      "type": "CVE-2021-44228",
      "title": "Log4j Remote Code Execution",
      "severity": "CRITICAL",
      "confidence": 0.99,
      "file": "/pom.xml",
      "lineNumber": 45,
      "column": 12,
      "affectedCode": "<log4j.version>2.14.0</log4j.version>",
      "description": "Apache Log4j2 <=2.14.1 JNDI features used in configuration",
      "cwe": ["CWE-502", "CWE-400"],
      "cvss": {
        "score": 10.0,
        "vector": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"
      },
      "fix": {
        "status": "generated",
        "description": "Update Log4j to version 2.21.1",
        "patch": "@@ -42,7 +42,7 @@\n-<log4j.version>2.14.0</log4j.version>\n+<log4j.version>2.21.1</log4j.version>",
        "confidence": 0.98,
        "breaking": false,
        "alternativeFixes": [
          {
            "description": "Remove JNDI lookup functionality",
            "confidence": 0.85
          }
        ]
      }
    }
  ],
  "metadata": {
    "scanEngine": "VulnPatcher v1.0.0",
    "vulnerabilityDatabases": ["CVE", "GHSA", "OSV"],
    "aiModels": {
      "detection": "mixtral:8x7b",
      "fixing": "deepseek-coder:33b"
    }
  }
}
```

### List All Scans

**GET** `/vulnpatcher/scans`

Lists all scans with pagination and filtering.

#### Query Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| page | integer | Page number (default: 1) |
| size | integer | Page size (default: 20) |
| status | enum | Filter by status |
| repository | string | Filter by repository URL |
| from | datetime | Scans after this date |
| to | datetime | Scans before this date |
| severity | enum | Filter by max severity found |
| sort | string | Sort field (createdAt, duration, vulnerabilities) |
| order | enum | Sort order (asc, desc) |

#### Response

```json
{
  "page": 1,
  "size": 20,
  "total": 145,
  "totalPages": 8,
  "items": [
    {
      "scanId": "scan-123",
      "repository": "https://github.com/example/repo",
      "branch": "main",
      "status": "completed",
      "vulnerabilitiesFound": 5,
      "createdAt": "2024-01-15T10:00:00Z",
      "duration": 120000
    }
  ]
}
```

### Cancel Scan

**POST** `/vulnpatcher/scan/{scanId}/cancel`

Cancels an in-progress scan.

#### Response

```json
{
  "scanId": "scan-123",
  "status": "cancelled",
  "message": "Scan cancelled successfully"
}
```

### Delete Scan

**DELETE** `/vulnpatcher/scan/{scanId}`

Deletes scan results and associated data.

#### Response

```json
{
  "message": "Scan deleted successfully"
}
```

## Workflow Management

### Start Vulnerability Fix Workflow

**POST** `/workflows/vulnerability-fix`

Initiates an AI-powered workflow to generate fixes for a specific vulnerability.

#### Request

```json
{
  "repositoryUrl": "https://github.com/example/repo",
  "branch": "main",
  "vulnerability": {
    "id": "CVE-2023-12345",
    "severity": "HIGH",
    "type": "SQL_INJECTION"
  },
  "context": {
    "filePath": "/src/main/java/UserService.java",
    "lineNumber": 42,
    "affectedCode": "String query = \"SELECT * FROM users WHERE id = \" + userId;",
    "language": "java",
    "framework": "Spring Boot",
    "dependencies": ["spring-boot-starter-data-jpa", "mysql-connector-java"]
  },
  "options": {
    "strategy": "SECURE",
    "preserveLogic": true,
    "addTests": true,
    "reviewRequired": true
  }
}
```

#### Response

```json
{
  "workflowId": "wf-456",
  "status": "running",
  "estimatedDuration": 30,
  "agents": [
    {
      "name": "SecurityEngineerAgent",
      "status": "active",
      "task": "Analyzing vulnerability"
    },
    {
      "name": "TechLeadReviewerAgent",
      "status": "waiting"
    }
  ],
  "links": {
    "events": "/api/v1/workflows/wf-456/events",
    "result": "/api/v1/workflows/wf-456/result"
  }
}
```

### Get Workflow Status

**GET** `/workflows/{workflowId}`

#### Response

```json
{
  "workflowId": "wf-456",
  "type": "vulnerability-fix",
  "status": "completed",
  "startTime": "2024-01-15T10:00:00Z",
  "endTime": "2024-01-15T10:00:30Z",
  "duration": 30000,
  "result": {
    "success": true,
    "fix": {
      "description": "Replace string concatenation with parameterized query",
      "code": "String query = \"SELECT * FROM users WHERE id = ?\";",
      "patch": "@@ -42,7 +42,7 @@\n-String query = \"SELECT * FROM users WHERE id = \" + userId;\n+String query = \"SELECT * FROM users WHERE id = ?\";\n+jdbcTemplate.queryForObject(query, new Object[]{userId}, User.class);",
      "confidence": 0.95,
      "testCode": "..."
    },
    "consensus": {
      "SecurityEngineerAgent": "approved",
      "TechLeadReviewerAgent": "approved",
      "SecurityExpertAgent": "approved"
    }
  }
}
```

### List Workflows

**GET** `/workflows`

Lists all workflows with filtering options.

#### Query Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| type | string | Workflow type filter |
| status | enum | Status filter |
| page | integer | Page number |
| size | integer | Page size |

## Vulnerability Operations

### Get Vulnerability Details

**GET** `/vulnerabilities/{vulnerabilityId}`

Retrieves detailed information about a specific vulnerability.

#### Response

```json
{
  "id": "CVE-2021-44228",
  "title": "Apache Log4j2 Remote Code Execution",
  "description": "Apache Log4j2 2.0-beta9 through 2.15.0 JNDI features...",
  "severity": "CRITICAL",
  "cvss": {
    "version": "3.1",
    "score": 10.0,
    "vector": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"
  },
  "cwe": ["CWE-502", "CWE-400", "CWE-20"],
  "affectedVersions": {
    "log4j-core": [">=2.0-beta9", "<=2.15.0"]
  },
  "patches": {
    "log4j-core": "2.16.0"
  },
  "references": [
    {
      "type": "WEB",
      "url": "https://nvd.nist.gov/vuln/detail/CVE-2021-44228"
    }
  ],
  "publishedDate": "2021-12-10T00:00:00Z",
  "lastModified": "2023-04-03T20:15:00Z"
}
```

### Search Vulnerabilities

**GET** `/vulnerabilities/search`

Search vulnerability database.

#### Query Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| q | string | Search query |
| severity | array | Severity levels |
| type | array | Vulnerability types |
| language | array | Programming languages |
| dateFrom | date | Published after |
| dateTo | date | Published before |

### Generate Fix

**POST** `/vulnerabilities/{vulnerabilityId}/generate-fix`

Generate a fix for a specific vulnerability in context.

#### Request

```json
{
  "context": {
    "code": "...",
    "filePath": "/src/main/java/Example.java",
    "language": "java",
    "framework": "Spring"
  },
  "strategy": "MINIMAL"
}
```

## Repository Management

### Clone Repository

**POST** `/repositories/clone`

Clones a repository for analysis.

#### Request

```json
{
  "url": "https://github.com/example/repo",
  "branch": "main",
  "credentials": {
    "token": "github_pat_xxx"
  }
}
```

### Update Repository

**POST** `/repositories/{repoId}/pull`

Updates a cloned repository.

### List Repositories

**GET** `/repositories`

Lists all managed repositories.

## Configuration

### Get Configuration

**GET** `/config`

Retrieves current configuration.

#### Response

```json
{
  "scanning": {
    "parallelFiles": 10,
    "maxFileSize": "10MB",
    "timeout": 3600
  },
  "ai": {
    "models": {
      "code": "deepseek-coder:33b",
      "analysis": "mixtral:8x7b"
    }
  },
  "sources": {
    "cve": {
      "enabled": true,
      "updateInterval": 3600
    }
  }
}
```

### Update Configuration

**PUT** `/config`

Updates system configuration.

## Health & Monitoring

### Health Check

**GET** `/health`

Returns system health status.

#### Response

```json
{
  "status": "UP",
  "components": {
    "ollama": {
      "status": "UP",
      "details": {
        "models": ["deepseek-coder:33b", "mixtral:8x7b"],
        "version": "0.1.24"
      }
    },
    "database": {
      "status": "UP",
      "details": {
        "type": "PostgreSQL",
        "version": "14.5"
      }
    },
    "memory": {
      "status": "UP",
      "details": {
        "heap_used_mb": 512,
        "heap_max_mb": 2048,
        "percentage": 25
      }
    }
  },
  "version": "1.0.0",
  "timestamp": "2024-01-15T10:00:00Z"
}
```

### Metrics

**GET** `/metrics`

Returns Prometheus-formatted metrics.

```
# HELP vulnpatcher_scans_total Total number of scans
# TYPE vulnpatcher_scans_total counter
vulnpatcher_scans_total{status="completed"} 1234
vulnpatcher_scans_total{status="failed"} 12

# HELP vulnpatcher_vulnerabilities_detected_total Total vulnerabilities detected
# TYPE vulnpatcher_vulnerabilities_detected_total counter
vulnpatcher_vulnerabilities_detected_total{severity="critical"} 45
vulnpatcher_vulnerabilities_detected_total{severity="high"} 123

# HELP vulnpatcher_scan_duration_seconds Scan duration in seconds
# TYPE vulnpatcher_scan_duration_seconds histogram
vulnpatcher_scan_duration_seconds_bucket{le="60"} 100
vulnpatcher_scan_duration_seconds_bucket{le="300"} 450
```

### Readiness Check

**GET** `/health/ready`

Checks if the service is ready to accept requests.

### Liveness Check

**GET** `/health/live`

Checks if the service is alive.

## WebSocket & SSE

### Stream Scan Progress

**GET** `/vulnpatcher/scan/{scanId}/stream`

Server-Sent Events endpoint for real-time scan updates.

#### Event Types

```
event: progress
data: {"percentage": 45, "message": "Scanning file 45 of 100", "currentFile": "/src/main/java/Example.java"}

event: vulnerability
data: {"id": "vuln-123", "severity": "HIGH", "file": "/pom.xml", "line": 42}

event: fix-generated
data: {"vulnerabilityId": "vuln-123", "confidence": 0.95}

event: error
data: {"code": "SCAN_ERROR", "message": "Failed to access file"}

event: complete
data: {"vulnerabilitiesFound": 12, "fixesGenerated": 10, "duration": 240000}
```

#### Example Usage

```javascript
const eventSource = new EventSource('/api/v1/vulnpatcher/scan/scan-123/stream', {
  headers: {
    'X-API-Key': apiKey
  }
});

eventSource.addEventListener('progress', (event) => {
  const data = JSON.parse(event.data);
  console.log(`Scan progress: ${data.percentage}%`);
});

eventSource.addEventListener('vulnerability', (event) => {
  const vuln = JSON.parse(event.data);
  console.log(`Found vulnerability: ${vuln.severity} in ${vuln.file}`);
});

eventSource.addEventListener('complete', (event) => {
  const result = JSON.parse(event.data);
  console.log(`Scan complete: ${result.vulnerabilitiesFound} vulnerabilities found`);
  eventSource.close();
});
```

### Stream Workflow Events

**GET** `/workflows/{workflowId}/events`

Real-time workflow progress updates.

```
event: agent-update
data: {"agent": "SecurityEngineerAgent", "status": "analyzing", "message": "Reviewing code context"}

event: consensus
data: {"agent": "TechLeadReviewerAgent", "decision": "approved", "confidence": 0.92}

event: fix-ready
data: {"confidence": 0.95, "breaking": false}
```

## Error Handling

### Error Response Format

All errors follow a consistent format:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid repository URL",
    "details": {
      "field": "repositoryUrl",
      "value": "not-a-url",
      "constraint": "Must be a valid Git repository URL"
    },
    "timestamp": "2024-01-15T10:00:00Z",
    "requestId": "req-123456"
  }
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| VALIDATION_ERROR | 400 | Invalid request parameters |
| AUTHENTICATION_FAILED | 401 | Invalid or missing API key |
| FORBIDDEN | 403 | Insufficient permissions |
| NOT_FOUND | 404 | Resource not found |
| RATE_LIMIT_EXCEEDED | 429 | Too many requests |
| INTERNAL_ERROR | 500 | Server error |
| SERVICE_UNAVAILABLE | 503 | Service temporarily unavailable |

### Rate Limiting

Rate limits are enforced per API key:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248000
```

## Code Examples

### Python Client

```python
import requests
import json
from typing import Dict, Any

class VulnPatcherClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url
        self.headers = {
            'X-API-Key': api_key,
            'Content-Type': 'application/json'
        }
    
    def start_scan(self, repo_url: str, **options) -> Dict[str, Any]:
        """Start a vulnerability scan"""
        payload = {
            'repositoryUrl': repo_url,
            **options
        }
        response = requests.post(
            f"{self.base_url}/vulnpatcher/scan",
            headers=self.headers,
            json=payload
        )
        response.raise_for_status()
        return response.json()
    
    def get_scan_results(self, scan_id: str) -> Dict[str, Any]:
        """Get scan results"""
        response = requests.get(
            f"{self.base_url}/vulnpatcher/scan/{scan_id}",
            headers=self.headers
        )
        response.raise_for_status()
        return response.json()
    
    def create_pr(self, scan_id: str, **pr_options) -> Dict[str, Any]:
        """Create pull request with fixes"""
        response = requests.post(
            f"{self.base_url}/vulnpatcher/scan/{scan_id}/create-pr",
            headers=self.headers,
            json=pr_options
        )
        response.raise_for_status()
        return response.json()

# Usage
client = VulnPatcherClient('http://localhost:8080/api/v1', 'your-api-key')

# Start scan
scan = client.start_scan(
    'https://github.com/example/repo',
    branch='main',
    severityThreshold='HIGH'
)

# Get results
results = client.get_scan_results(scan['scanId'])

# Create PR if vulnerabilities found
if results['summary']['vulnerabilitiesFound'] > 0:
    pr = client.create_pr(
        scan['scanId'],
        title='Security fixes',
        description='Automated security fixes by VulnPatcher'
    )
```

### JavaScript/TypeScript Client

```typescript
interface ScanOptions {
  branch?: string;
  languages?: string[];
  severityThreshold?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}

class VulnPatcherClient {
  constructor(
    private baseUrl: string,
    private apiKey: string
  ) {}

  async startScan(repoUrl: string, options?: ScanOptions): Promise<any> {
    const response = await fetch(`${this.baseUrl}/vulnpatcher/scan`, {
      method: 'POST',
      headers: {
        'X-API-Key': this.apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        repositoryUrl: repoUrl,
        ...options
      })
    });

    if (!response.ok) {
      throw new Error(`Scan failed: ${response.statusText}`);
    }

    return response.json();
  }

  streamProgress(scanId: string, onProgress: (data: any) => void): EventSource {
    const eventSource = new EventSource(
      `${this.baseUrl}/vulnpatcher/scan/${scanId}/stream`
    );

    eventSource.addEventListener('progress', (event) => {
      onProgress(JSON.parse(event.data));
    });

    return eventSource;
  }
}
```

### Java Client (using Quarkus REST Client)

```java
@Path("/api/v1")
@RegisterRestClient(configKey = "vulnpatcher-api")
public interface VulnPatcherClient {
    
    @POST
    @Path("/vulnpatcher/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<ScanResponse> startScan(
        @HeaderParam("X-API-Key") String apiKey,
        ScanRequest request
    );
    
    @GET
    @Path("/vulnpatcher/scan/{scanId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<ScanResults> getScanResults(
        @HeaderParam("X-API-Key") String apiKey,
        @PathParam("scanId") String scanId
    );
    
    @GET
    @Path("/vulnpatcher/scan/{scanId}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<ScanEvent> streamProgress(
        @HeaderParam("X-API-Key") String apiKey,
        @PathParam("scanId") String scanId
    );
}
```

## OpenAPI Specification

The complete OpenAPI 3.0 specification is available at:

```
http://localhost:8080/openapi
```

Swagger UI interface:

```
http://localhost:8080/swagger-ui
```

---

Last Updated: January 2024
Version: 1.0.0