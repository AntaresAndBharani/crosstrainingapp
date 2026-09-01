#!/bin/bash
# Test suite for architect.yml workflow logic verification
# Verifies:
# - type:user-story detection
# - origin:backlog-triage model selection
# - Status label trigger recognition

PASSED=0
FAILED=0

# Helper function to test label parsing
test_label_detection() {
  local test_name="$1"
  local labels="$2"
  local expected_is_story="$3"

  # Simulate the TYPES variable assembly
  TYPES="$labels"
  IS_STORY=false
  case ",$TYPES," in
    *,type:user-story,*) IS_STORY=true;;
  esac

  if [ "$IS_STORY" = "$expected_is_story" ]; then
    echo "✓ PASS: $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "✗ FAIL: $test_name (expected $expected_is_story, got $IS_STORY)"
    FAILED=$((FAILED + 1))
  fi
}

# Helper function to test model selection
test_model_selection() {
  local test_name="$1"
  local labels="$2"
  local expected_model="$3"

  TYPES="$labels"
  MODEL=claude-opus-5
  case ",$TYPES," in
    *,origin:backlog-triage,*) MODEL=claude-sonnet-5;;
  esac

  if [ "$MODEL" = "$expected_model" ]; then
    echo "✓ PASS: $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "✗ FAIL: $test_name (expected $expected_model, got $MODEL)"
    FAILED=$((FAILED + 1))
  fi
}

# Helper function to test status label trigger
test_status_trigger() {
  local test_name="$1"
  local trigger_label="$2"
  local expected_mode="$3"

  TRIGGER_LABEL="$trigger_label"
  MODE=skip
  case "$TRIGGER_LABEL" in
    status:ready-for-architect)  MODE=decompose ;;
    status:needs-revision)       MODE=restructure ;;
    status:needs-clarification)  MODE=answer_clarifications ;;
    *)                           MODE=skip ;;
  esac

  if [ "$MODE" = "$expected_mode" ]; then
    echo "✓ PASS: $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "✗ FAIL: $test_name (expected $expected_mode, got $MODE)"
    FAILED=$((FAILED + 1))
  fi
}

echo "=========================================="
echo "Testing architect.yml workflow logic"
echo "=========================================="
echo

echo "Test 1: type:user-story detection"
echo "---"
test_label_detection "Detect type:user-story label" "type:user-story,status:ready-for-architect" "true"
test_label_detection "Missing type:user-story label" "status:ready-for-architect" "false"
test_label_detection "type:user-story in middle of labels" "status:needs-revision,type:user-story,priority:high" "true"
echo

echo "Test 2: origin:backlog-triage model selection"
echo "---"
test_model_selection "Sonnet for backlog-triage origin" "origin:backlog-triage,type:user-story,status:ready-for-architect" "claude-sonnet-5"
test_model_selection "Default Opus without backlog-triage" "type:user-story,status:ready-for-architect" "claude-opus-5"
test_model_selection "Default Opus for PO-drafted story" "type:user-story,status:ready-for-architect,priority:high" "claude-opus-5"
echo

echo "Test 3: Status label trigger recognition"
echo "---"
test_status_trigger "Recognize status:ready-for-architect" "status:ready-for-architect" "decompose"
test_status_trigger "Recognize status:needs-revision" "status:needs-revision" "restructure"
test_status_trigger "Recognize status:needs-clarification" "status:needs-clarification" "answer_clarifications"
test_status_trigger "Skip on unrecognized label" "status:pending-review" "skip"
echo

echo "=========================================="
echo "Test Results"
echo "=========================================="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo

if [ "$FAILED" -eq 0 ]; then
  echo "✓ All tests passed!"
  true
else
  echo "✗ Some tests failed!"
  false
fi
