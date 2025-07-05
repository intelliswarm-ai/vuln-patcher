#!/bin/bash

# Test Coverage Report Script
# Generates comprehensive test coverage reports for VulnPatcher

echo "========================================="
echo "VulnPatcher Test Coverage Report"
echo "========================================="

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Run tests with coverage
echo -e "${YELLOW}Running tests with JaCoCo coverage...${NC}"
mvn clean test jacoco:report

# Check if tests passed
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed${NC}"
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi

# Parse coverage report
COVERAGE_FILE="target/site/jacoco/index.html"
if [ -f "$COVERAGE_FILE" ]; then
    # Extract coverage percentage (simple parsing)
    COVERAGE=$(grep -o '[0-9]\+%' "$COVERAGE_FILE" | head -1 | tr -d '%')
    
    echo "========================================="
    echo -e "Overall Code Coverage: ${GREEN}${COVERAGE}%${NC}"
    echo "========================================="
    
    if [ "$COVERAGE" -ge 98 ]; then
        echo -e "${GREEN}✓ Coverage exceeds 98% requirement!${NC}"
    else
        echo -e "${RED}✗ Coverage is below 98% requirement${NC}"
        echo "Please add more tests to increase coverage"
    fi
else
    echo -e "${RED}Coverage report not found${NC}"
fi

# Generate detailed report
echo ""
echo "Generating detailed coverage report..."
mvn jacoco:report-aggregate

# Count test statistics
echo ""
echo "Test Statistics:"
echo "----------------"
UNIT_TESTS=$(find . -name "*Test.java" | grep -v integration | wc -l)
INTEGRATION_TESTS=$(find . -name "*IntegrationTest.java" | wc -l)
TOTAL_TESTS=$((UNIT_TESTS + INTEGRATION_TESTS))

echo "Unit Tests: $UNIT_TESTS"
echo "Integration Tests: $INTEGRATION_TESTS"
echo "Total Test Files: $TOTAL_TESTS"

# Count test methods
TEST_METHODS=$(grep -r "@Test" src/test/java | wc -l)
echo "Total Test Methods: $TEST_METHODS"

# List uncovered classes (if any)
echo ""
echo "Checking for uncovered classes..."
mvn org.pitest:pitest-maven:mutationCoverage > /dev/null 2>&1

echo ""
echo "========================================="
echo "Coverage report available at:"
echo "target/site/jacoco/index.html"
echo "========================================="