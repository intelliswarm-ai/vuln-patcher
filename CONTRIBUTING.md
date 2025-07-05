# Contributing to VulnPatcher

First off, thank you for considering contributing to VulnPatcher! It's people like you that make VulnPatcher such a great tool.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [How Can I Contribute?](#how-can-i-contribute)
4. [Development Process](#development-process)
5. [Style Guidelines](#style-guidelines)
6. [Commit Guidelines](#commit-guidelines)
7. [Pull Request Process](#pull-request-process)
8. [Community](#community)

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code.

### Our Standards

- Using welcoming and inclusive language
- Being respectful of differing viewpoints and experiences
- Gracefully accepting constructive criticism
- Focusing on what is best for the community
- Showing empathy towards other community members

### Our Responsibilities

Project maintainers are responsible for clarifying the standards of acceptable behavior and are expected to take appropriate and fair corrective action in response to any instances of unacceptable behavior.

## Getting Started

### Prerequisites

1. Java 17 or higher
2. Maven 3.8+
3. Ollama installed and running
4. Git

### Setting Up Your Development Environment

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/your-username/vuln-patcher.git
   cd vuln-patcher
   ```

3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/intelliswarm/vuln-patcher.git
   ```

4. Create a branch for your feature:
   ```bash
   git checkout -b feature/your-feature-name
   ```

5. Set up your environment:
   ```bash
   cp .env.example .env
   # Edit .env with your credentials
   source .env
   ```

6. Build the project:
   ```bash
   mvn clean install
   ```

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- System information (OS, Java version, etc.)
- Relevant logs or error messages
- Code samples if applicable

**Bug Report Template:**

```markdown
## Description
A clear and concise description of the bug.

## Steps to Reproduce
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened.

## Environment
- OS: [e.g., Ubuntu 22.04]
- Java Version: [e.g., 17.0.5]
- VulnPatcher Version: [e.g., 1.0.0]
- Ollama Version: [e.g., 0.1.24]

## Additional Context
Add any other context, logs, or screenshots.
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- A clear and descriptive title
- A detailed description of the proposed enhancement
- Explain why this enhancement would be useful
- List any alternative solutions you've considered

**Enhancement Template:**

```markdown
## Summary
Brief description of the enhancement.

## Motivation
Why is this enhancement needed? What problem does it solve?

## Detailed Description
A detailed description of the proposed enhancement.

## Alternatives Considered
What alternative solutions have you considered?

## Additional Context
Any additional information, mockups, or examples.
```

### Contributing Code

#### First-Time Contributors

Look for issues labeled with `good first issue` or `help wanted`. These are great starting points for new contributors.

#### Areas We Need Help

- **New Vulnerability Sources**: Adding support for additional vulnerability databases
- **Language Support**: Adding fix generators for new programming languages
- **AI Improvements**: Enhancing the AI agents and fix generation
- **Performance**: Optimizing scanning and processing speed
- **Documentation**: Improving guides and API documentation
- **Testing**: Adding more unit and integration tests
- **UI/UX**: If we add a web interface in the future

## Development Process

### 1. Create an Issue

Before starting work, create an issue describing what you plan to do. This helps avoid duplicate work and allows for discussion.

### 2. Work on Your Feature

```bash
# Keep your fork up to date
git fetch upstream
git checkout main
git merge upstream/main

# Create a feature branch
git checkout -b feature/your-feature

# Make your changes
# ... edit files ...

# Run tests
mvn test

# Check code quality
mvn spotless:check
mvn spotbugs:check
```

### 3. Write Tests

All new features must include tests:

```java
@QuarkusTest
class YourFeatureTest {
    
    @Test
    void testYourFeature() {
        // Given
        // When
        // Then
    }
}
```

### 4. Update Documentation

- Update README.md if needed
- Add/update JavaDoc comments
- Update configuration guides if adding new properties
- Add examples if introducing new features

## Style Guidelines

### Java Code Style

We follow the Google Java Style Guide with some modifications:

```java
// Package statement
package ai.intelliswarm.vulnpatcher.service;

// Import statements (ordered)
import ai.intelliswarm.vulnpatcher.model.Vulnerability;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

/**
 * Service for managing vulnerabilities.
 * 
 * @author Your Name
 */
@ApplicationScoped
public class VulnerabilityService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VulnerabilityService.class);
    
    @Inject
    VulnerabilityRepository repository;
    
    /**
     * Finds vulnerabilities by severity.
     * 
     * @param severity the severity level
     * @return list of vulnerabilities
     */
    public List<Vulnerability> findBySeverity(String severity) {
        LOGGER.debug("Finding vulnerabilities with severity: {}", severity);
        return repository.find("severity", severity).list();
    }
}
```

### Code Conventions

1. **Naming**:
   - Classes: `PascalCase`
   - Methods/Variables: `camelCase`
   - Constants: `UPPER_SNAKE_CASE`
   - Packages: `lowercase`

2. **Formatting**:
   - Indentation: 4 spaces
   - Line length: 120 characters
   - Always use braces for if/for/while

3. **Best Practices**:
   - Prefer immutability
   - Use Optional for nullable returns
   - Leverage Quarkus DI
   - Write reactive code where possible

### Testing Guidelines

```java
@QuarkusTest
class ServiceTest {
    
    @Inject
    Service service;
    
    @Test
    @DisplayName("Should successfully process valid input")
    void testValidInput() {
        // Given
        String input = "valid";
        
        // When
        Result result = service.process(input);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
    }
}
```

## Commit Guidelines

We follow Conventional Commits specification:

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation changes
- **style**: Code style changes (formatting, etc.)
- **refactor**: Code refactoring
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **build**: Build system changes
- **ci**: CI/CD changes
- **chore**: Maintenance tasks

### Examples

```bash
# Feature
git commit -m "feat(scanner): add support for Ruby vulnerability detection"

# Bug fix
git commit -m "fix(ai): resolve timeout issue in fix generation"

# Documentation
git commit -m "docs(api): update REST API documentation"

# With breaking change
git commit -m "feat(api)!: change scan endpoint response format

BREAKING CHANGE: The scan endpoint now returns a different JSON structure.
Old format: {status: 'success', data: {...}}
New format: {success: true, result: {...}}"
```

### Commit Best Practices

1. Keep commits atomic and focused
2. Write clear, descriptive commit messages
3. Reference issues when applicable: `fixes #123`
4. Sign your commits: `git commit -s`

## Pull Request Process

### Before Submitting

1. **Test your changes**:
   ```bash
   mvn clean test
   mvn verify
   ```

2. **Check code quality**:
   ```bash
   mvn spotless:apply
   mvn spotbugs:check
   ```

3. **Update documentation**

4. **Add tests** for new functionality

5. **Ensure CI passes**

### PR Template

```markdown
## Description
Brief description of the changes.

## Type of Change
- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update

## Related Issues
Fixes #(issue number)

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] My code follows the project style guidelines
- [ ] I have performed a self-review
- [ ] I have commented my code where necessary
- [ ] I have updated the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix/feature works
- [ ] New and existing tests pass locally
- [ ] Any dependent changes have been merged
```

### Review Process

1. At least one maintainer approval required
2. All CI checks must pass
3. No merge conflicts
4. Code coverage should not decrease

### After Your PR is Merged

1. Delete your feature branch
2. Update your local main branch
3. Celebrate! 🎉

## Community

### Communication Channels

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and discussions
- **Email**: support@intelliswarm.ai

### Getting Help

If you need help, you can:

1. Check the documentation
2. Search existing issues
3. Ask in GitHub Discussions
4. Contact the maintainers

### Recognition

Contributors are recognized in:
- The project README
- Release notes
- The contributors page

## Development Tips

### Running in Dev Mode

```bash
mvn quarkus:dev
```

### Debugging

```bash
mvn quarkus:dev -Ddebug=5005
```

### Running Specific Tests

```bash
# Single test
mvn test -Dtest=VulnerabilityServiceTest

# Test class pattern
mvn test -Dtest=*ServiceTest

# With debugging
mvn test -Dtest=VulnerabilityServiceTest -Dmaven.surefire.debug
```

### Performance Testing

```bash
# Run with profiling
mvn quarkus:dev -Dquarkus.profile=perf

# Generate performance report
mvn verify -Pperformance
```

## License

By contributing to VulnPatcher, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to VulnPatcher! Your efforts help make software more secure for everyone. 🚀