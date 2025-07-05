# VulnPatcher Architecture

## Overview

VulnPatcher is a comprehensive vulnerability scanning and auto-patching system built with Quarkus, LangChain4j, and LangGraph. It uses a multi-agent AI architecture to detect vulnerabilities in code repositories and automatically generate fixes.

## System Components

### 1. Vulnerability Database Module
- **Purpose**: Fetches and aggregates vulnerability data from multiple sources
- **Sources**: CVE, GHSA, OSV, OSS-Index, Snyk, OVAL
- **Storage**: In-memory cache with persistent storage option
- **Update Strategy**: Scheduled updates with incremental sync

### 2. Git Repository Scanner
- **Purpose**: Scans local and remote Git repositories
- **Supports**: GitHub, GitLab, Bitbucket, local repos
- **Features**: 
  - Recursive dependency scanning
  - Multi-language support
  - Incremental scanning

### 3. Vulnerability Detection Engine
- **Purpose**: Matches code patterns against vulnerability database
- **Techniques**:
  - Dependency version checking
  - Code pattern matching
  - AST analysis for code vulnerabilities
  - SAST integration

### 4. Multi-Agent AI System (MCP-based)
- **Coordinator Agent**: Orchestrates the workflow
- **Analyzer Agents**: Language-specific vulnerability analysis
- **Fix Generator Agents**: Creates patches for detected vulnerabilities
- **Review Agent**: Validates proposed fixes
- **PR Agent**: Creates and manages pull requests

### 5. Language-Specific Fix Generators
Supported languages:
- Java (Maven, Gradle)
- Python (pip, poetry)
- JavaScript/TypeScript (npm, yarn)
- C++ (CMake, conan)
- Kotlin
- Angular/React
- SQL

### 6. Pull Request Manager
- Creates PRs with detailed descriptions
- Includes vulnerability details and fix explanations
- Supports GitHub, GitLab, Bitbucket APIs

## Data Flow

1. **Vulnerability Collection**: Scheduled jobs fetch latest vulnerability data
2. **Repository Analysis**: Scanner analyzes target repository
3. **Detection**: Engine matches vulnerabilities with code
4. **Fix Generation**: AI agents generate appropriate patches
5. **Validation**: Review agent validates fixes
6. **PR Creation**: System creates pull request with fixes

## Context Management for Large Codebases

### Chunking Strategy
- **Chunk Size**: 1500 tokens with 200 token overlap
- **Vector Storage**: In-memory embeddings with semantic search
- **Streaming Analysis**: Process files in batches to avoid memory overflow
- **Smart Retrieval**: RAG-based context retrieval for relevant code sections

### Scalability Features
1. **Parallel Processing**: Multi-threaded file analysis
2. **Incremental Scanning**: Only process changed files
3. **Context Window Management**: Intelligent chunk selection based on relevance
4. **Session-based Storage**: Isolated context per scan session
5. **LRU Eviction**: Automatic cleanup of old context data

## Technology Stack

- **Framework**: Quarkus 3.9.0
- **AI/ML**: LangChain4j 0.31.0 with Ollama
- **LLM Models**: 
  - Code Generation: DeepSeek Coder 33B
  - Analysis: Mixtral 8x7B
  - Review: CodeLlama 34B
- **Embeddings**: All-MiniLM-L6-v2 (local)
- **Graph Processing**: LangGraph
- **Git Operations**: JGit, GitHub API, GitLab4j
- **HTTP Client**: Apache HttpClient 5
- **Testing**: JUnit 5, Mockito, RestAssured

## Security Considerations

- API keys stored in secure configuration
- Rate limiting for external API calls
- Sandboxed code execution for fix validation
- Audit logging for all operations
- Local LLM processing for sensitive code