#!/bin/bash

# VulnPatcher Test Runner Script
# This script runs all tests and generates comprehensive reports

set -e

echo "=================================================="
echo "VulnPatcher Test Suite Runner"
echo "=================================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e "${YELLOW}Warning: Ollama is not running. Some tests may fail.${NC}"
    echo "Start Ollama with: ollama serve"
    echo ""
fi

# Clean previous test results
echo "Cleaning previous test results..."
rm -rf target/surefire-reports target/site/jacoco

# Run unit tests
echo ""
echo "Running unit tests..."
echo "=================================================="
mvn clean test -Dtest="!*IntegrationTest,!*IT" || {
    echo -e "${RED}Unit tests failed${NC}"
    exit 1
}

# Run integration tests
echo ""
echo "Running integration tests..."
echo "=================================================="
mvn test -Dtest="*IntegrationTest,*IT" || {
    echo -e "${YELLOW}Integration tests failed (this may be expected if external services are not available)${NC}"
}

# Generate coverage report
echo ""
echo "Generating coverage report..."
echo "=================================================="
mvn jacoco:report

# Display coverage summary
echo ""
echo "Test Coverage Summary:"
echo "=================================================="
if [ -f "target/site/jacoco/index.html" ]; then
    # Extract coverage percentages from the HTML report
    COVERAGE=$(grep -oP 'Total.*?(\d+)%' target/site/jacoco/index.html | head -1 | grep -oP '\d+' || echo "0")
    
    if [ "$COVERAGE" -ge 98 ]; then
        echo -e "${GREEN}✓ Code coverage: ${COVERAGE}% (Target: 98%)${NC}"
    else
        echo -e "${YELLOW}⚠ Code coverage: ${COVERAGE}% (Target: 98%)${NC}"
    fi
    
    echo ""
    echo "Detailed coverage report available at: target/site/jacoco/index.html"
fi

# Run specific test suites
echo ""
echo "Running specific test suites..."
echo "=================================================="

# Test 1: Application Startup
echo -n "1. Application Startup Test: "
if mvn test -Dtest=ApplicationStartupTest -q; then
    echo -e "${GREEN}✓ PASSED${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi

# Test 2: Vulnerability Sources
echo -n "2. Vulnerability Sources Test: "
if timeout 300 mvn test -Dtest=VulnerabilitySourcesIntegrationTest -q; then
    echo -e "${GREEN}✓ PASSED${NC}"
else
    echo -e "${YELLOW}⚠ SKIPPED/FAILED (External services may be unavailable)${NC}"
fi

# Test 3: End-to-End Test
echo -n "3. End-to-End Integration Test: "
if mvn test -Dtest=VulnPatcherEndToEndTest -q; then
    echo -e "${GREEN}✓ PASSED${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi

# Test 4: AI Agent Tests
echo -n "4. AI Agent Tests: "
if mvn test -Dtest="*Agent*Test" -q; then
    echo -e "${GREEN}✓ PASSED${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi

# Test 5: Fix Generator Tests
echo -n "5. Fix Generator Tests: "
if mvn test -Dtest="*FixGenerator*Test" -q; then
    echo -e "${GREEN}✓ PASSED${NC}"
else
    echo -e "${RED}✗ FAILED${NC}"
fi

# Generate test report
echo ""
echo "Generating test report..."
echo "=================================================="
mvn surefire-report:report-only

# Summary
echo ""
echo "=================================================="
echo "Test Execution Summary"
echo "=================================================="

# Count test results
TOTAL_TESTS=$(find target/surefire-reports -name "*.xml" 2>/dev/null | xargs grep -h "tests=" | grep -oP 'tests="\d+"' | grep -oP '\d+' | awk '{s+=$1} END {print s}' || echo "0")
FAILED_TESTS=$(find target/surefire-reports -name "*.xml" 2>/dev/null | xargs grep -h "failures=" | grep -oP 'failures="\d+"' | grep -oP '\d+' | awk '{s+=$1} END {print s}' || echo "0")
ERRORS=$(find target/surefire-reports -name "*.xml" 2>/dev/null | xargs grep -h "errors=" | grep -oP 'errors="\d+"' | grep -oP '\d+' | awk '{s+=$1} END {print s}' || echo "0")
SKIPPED=$(find target/surefire-reports -name "*.xml" 2>/dev/null | xargs grep -h "skipped=" | grep -oP 'skipped="\d+"' | grep -oP '\d+' | awk '{s+=$1} END {print s}' || echo "0")

echo "Total tests run: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $((TOTAL_TESTS - FAILED_TESTS - ERRORS - SKIPPED))${NC}"
echo -e "${RED}Failed: $FAILED_TESTS${NC}"
echo -e "${RED}Errors: $ERRORS${NC}"
echo -e "${YELLOW}Skipped: $SKIPPED${NC}"

echo ""
echo "Reports available at:"
echo "- Test report: target/surefire-reports/"
echo "- Coverage report: target/site/jacoco/index.html"
echo ""

# Check if we met the coverage requirement
if [ "$COVERAGE" -ge 98 ]; then
    echo -e "${GREEN}✓ All quality gates passed!${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠ Coverage target not met (${COVERAGE}% < 98%)${NC}"
    exit 1
fi