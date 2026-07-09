# RCA Validation JSON Schema - Complete Explanation

## Overview

This document explains the complete structure of the **validation persistence data** that gets saved to the database and how the scoring/confidence calculations work.

---

## Database Schema

```sql
validation_result VARCHAR(64)    -- Final verdict: "SUPPORTED", "PARTIALLY_SUPPORTED", "UNSUPPORTED"
validation_data   JSONB           -- Complete validation details (JSON explained below)
```

---

## Validation Weights (PATH A vs PATH B)

The dual validation pipeline combines two approaches with weighted aggregation:

```java
PATH A (Assertion-based LLM):    40% weight  (0.4)
PATH B (Rule-based deterministic): 60% weight  (0.6)
```

**Why 40/60?**
- **PATH B (60%)**: Deterministic rules are faster, more reliable, and less prone to hallucination for known failure patterns
- **PATH A (40%)**: LLM-based validation is more exploratory and can find nuanced evidence but is slower and sometimes uncertain

**Final Confidence Calculation:**
```
Final Confidence = (0.4 × PATH_A_confidence) + (0.6 × PATH_B_confidence)
```

**Example:**
```
PATH A confidence: 0.75
PATH B confidence: 0.89

Final = (0.4 × 0.75) + (0.6 × 0.89)
      = 0.30 + 0.534
      = 0.834 (83.4% confidence - HIGH CONFIDENCE ✅)
```

---

## PATH B: Rule-Based Scoring (totalScore)

### How `totalScore` is Calculated

PATH B evaluates the hypothesis (e.g., "OOMKilled") using a rule set with 3 types of rules:

```
totalScore = Required Rules Count + Supporting Rules Weight - Exclusion Rules Weight
```

#### Rule Types:

1. **REQUIRED Rules** (gating conditions)
   - **Weight**: 1 point each (just a count)
   - **Behavior**: ALL must pass, or hypothesis is immediately UNSUPPORTED
   - **Example**: Exit code = 137, Termination reason = "OOMKilled"

2. **SUPPORTING Rules** (positive evidence)
   - **Weight**: Variable (typically 2-5 points each)
   - **Behavior**: Add weight when matched, strengthen confidence
   - **Example**: Heap usage > 90% (+5 points), OOM error in logs (+5 points)

3. **EXCLUSION Rules** (negative evidence)
   - **Weight**: Negative values (typically -7 to -10 points)
   - **Behavior**: Subtract weight when matched, weaken or invalidate hypothesis
   - **Example**: Normal exit code 0 (-10 points), Deployment rollout (-10 points)

#### Example Calculation (OOMKilled):

```
Required Rules:
  ✅ exit_code_137                → passed (count = 1)
  ✅ reason_oomkilled             → passed (count = 1)
  ✅ memory_increasing            → passed (count = 1)
  ✅ no_manual_restart            → passed (count = 1)
  Required Score = 4

Supporting Rules:
  ✅ heap_exceeded (heap > 90%)   → matched (weight = +5)
  ❌ frequent_full_gc             → not matched (weight = 0)
  ❌ heap_trend_increasing        → not matched (weight = 0)
  ❌ allocation_increasing        → not matched (weight = 0)
  ❌ kruize_recommends_memory     → not matched (weight = 0)
  ✅ oom_error_in_logs            → matched (weight = +5)
  ❌ memory_pressure              → not matched (weight = 0)
  Supporting Score = +10

Exclusion Rules:
  ❌ deployment_rollout           → not matched (weight = 0)
  ❌ node_disk_pressure           → not matched (weight = 0)
  ❌ crashloop_config             → not matched (weight = 0)
  ❌ normal_exit                  → not matched (weight = 0)
  Exclusion Score = 0

TOTAL SCORE = 4 + 10 + 0 = 14
```

### Confidence Calculation from Score

Once we have the `totalScore`, confidence is calculated based on thresholds:

```java
if (status == SUPPORTED) {
    // Map score to 0.8 - 1.0 range
    int supportedThreshold = 6;   // Minimum score for SUPPORTED
    int maxScore = 26;            // supportedThreshold + 20
    double normalized = min(1.0, score / maxScore);
    confidence = 0.8 + (normalized × 0.2);
}
```

**Example:**
```
totalScore = 14
supportedThreshold = 6
maxScore = 26

normalized = min(1.0, 14 / 26) = min(1.0, 0.538) = 0.538
confidence = 0.8 + (0.538 × 0.2) = 0.8 + 0.108 = 0.908 (90.8%)
```

### Should We Normalize totalScore?

**Current State**: `totalScore` is a raw sum (e.g., 14 points)

**Option 1: Keep Raw Score** (Current)
```json
{
  "totalScore": 14,
  "confidence": 0.89
}
```
**Pros:**
- Absolute score shows how many rules matched
- Easy to understand: 14 points = 4 required + 10 supporting
- Useful for debugging (you can see the exact contribution)

**Cons:**
- Score range varies by rule set (OOMKilled max ≠ CPUThrottling max)
- Hard to compare across different hypotheses

**Option 2: Normalized Score** (0.0 - 1.0)
```json
{
  "totalScore": 14,
  "normalizedScore": 0.538,  // 14 / 26
  "confidence": 0.89
}
```
**Pros:**
- Comparable across different rule sets
- Easier to interpret: 0.538 = 53.8% of max possible score

**Cons:**
- Need to expose `maxScore` in JSON
- Adds complexity

**Recommendation**: **Add `normalizedScore` field** alongside `totalScore`
- Keep `totalScore` for transparency (shows exact rule weights)
- Add `normalizedScore` for easier comparison
- Add `maxPossibleScore` for context

```json
{
  "totalScore": 14,
  "maxPossibleScore": 26,
  "normalizedScore": 0.538,
  "confidence": 0.89
}
```

---

## Complete JSON Schema Explanation

### Top-Level Structure

```json
{
  "dualValidation": { ... },      // PATH A + PATH B results + final verdict
  "summary": { ... },              // High-level summary metrics
  "validationResults": [ ... ],    // Detailed assertion results
  "validatedAt": "ISO-8601 timestamp"
}
```

---

### 1. `dualValidation` Object

#### 1.1 `assertionBasedVerdict` (PATH A - LLM)

```json
"assertionBasedVerdict": {
  "status": "SUPPORTED",           // Overall assertion status
  "confidence": 0.758,             // Average confidence across all assertions
  "totalAssertions": 5,            // How many assertions extracted from RCA
  "supportedAssertions": 4,        // Count with status = SUPPORTED
  "partiallySupportedAssertions": 0,
  "unsupportedAssertions": 0,
  "unknownAssertions": 1,          // Recommendations aren't validated
  "explanation": "Assertions: 4 supported, 0 partial, 0 unsupported, 1 unknown out of 5 total"
}
```

#### 1.2 `ruleBasedVerdict` (PATH B - Deterministic)

```json
"ruleBasedVerdict": {
  "hypothesis": "OOMKilled",       // Which hypothesis was tested
  "status": "SUPPORTED",           // Result: SUPPORTED | PARTIALLY_SUPPORTED | UNSUPPORTED
  "confidence": 0.8933,            // Confidence (0.8-1.0 for SUPPORTED)
  "totalScore": 14,                // Raw score = required count + supporting weight - exclusion weight
  "requiredResults": [ ... ],      // Detailed results for each REQUIRED rule
  "supportingResults": [ ... ],    // Detailed results for each SUPPORTING rule
  "exclusionResults": [ ... ],     // Detailed results for each EXCLUSION rule
  "explanation": "Required: 4/4 passed. Supporting: 2 matched. Total score: 14. Verdict: SUPPORTED."
}
```

##### 1.2.1 `requiredResults` Array

Each required rule evaluation:

```json
{
  "rule": {
    "id": "oom.required.exit_code_137",
    "description": "Container terminated with exit code 137 (SIGKILL)",
    "type": "REQUIRED",
    "weight": 1
  },
  "passed": true,                    // Did this rule match?
  "matchedSignals": [                // Which signals satisfied this rule?
    {
      "type": "CONTAINER_STATUS",    // Signal category
      "name": "exitCode",            // Signal identifier
      "value": 137,                  // Raw value (could be string, int, boolean, etc.)
      "metadata": {},                // Additional context (usually empty)
      "valueAsInt": 137,             // Typed accessors for convenience
      "valueAsDouble": 137.0,
      "valueAsBoolean": null,
      "valueAsString": "137"
    }
  ],
  "reasoning": "Found container exit code 137",  // Why this rule passed/failed
  "weightContribution": 1,           // How many points this rule added
  "failed": false                    // Inverse of "passed"
}
```

**What is a Signal?**

A **Signal** is a structured piece of evidence extracted from the MCP diagnostic context:

```
Raw MCP Context → Signal Extractor → Structured Signals → Rule Matcher
```

**Example Signal Extraction:**

```
Raw Context:
  "Container exit code: 137"
  "Termination reason: OOMKilled"

↓ Signal Extractor

Structured Signals:
  { type: "CONTAINER_STATUS", name: "exitCode", value: 137 }
  { type: "KUBERNETES_EVENT", name: "terminationReason", value: "OOMKilled" }
```

**Signal Types:**
- `KUBERNETES_EVENT`: K8s events (pod created, container started, OOMKilled, etc.)
- `CONTAINER_STATUS`: Container state (exit code, restart count, termination reason)
- `POD_STATUS`: Pod state (phase, conditions, restart policy)
- `METRIC`: Metrics (CPU usage, memory usage, trends)
- `LOG_PATTERN`: Patterns found in logs (OutOfMemoryError, exceptions)

##### 1.2.2 `supportingResults` Array

Same structure as `requiredResults`, but:
- **Weight**: Variable (2-5 points typically)
- **Behavior**: Not required, but boost confidence when matched

```json
{
  "rule": {
    "id": "oom.supporting.heap_exceeded",
    "description": "Heap usage exceeded 90% threshold",
    "type": "SUPPORTING",
    "weight": 5                      // Higher weight than required (which is always 1)
  },
  "passed": true,
  "matchedSignals": [
    {
      "type": "METRIC",
      "name": "heap.usage",
      "value": 0.98,                 // 98% heap usage
      "valueAsDouble": 0.98
    }
  ],
  "reasoning": "Heap usage exceeded 90%",
  "weightContribution": 5,           // Added 5 points to totalScore
  "failed": false
}
```

##### 1.2.3 `exclusionResults` Array

Same structure, but:
- **Weight**: Negative values (-7 to -10)
- **Behavior**: When matched, these SUBTRACT from score or invalidate hypothesis

```json
{
  "rule": {
    "id": "oom.exclusion.normal_exit",
    "description": "Application exited normally (exit code 0)",
    "type": "EXCLUSION",
    "weight": -10                    // Negative weight
  },
  "passed": false,                   // Good! We DON'T want this to match
  "matchedSignals": [],
  "reasoning": "No normal exit detected",
  "weightContribution": 0,           // Didn't match, so no penalty
  "failed": true                     // "failed" = didn't match (which is good here)
}
```

#### 1.3 `finalVerdict` (Aggregated Result)

```json
"finalVerdict": {
  "status": "SUPPORTED",             // Final decision after combining PATH A + B
  "confidence": 0.8345,              // (0.4 × 0.758) + (0.6 × 0.893) = 0.8345
  "strategy": "WEIGHTED_AVERAGE",    // How paths were combined
  "explanation": "Weighted average: assertion=1.00 (weight=0.4), rule=1.00 (weight=0.6), final=1.00"
}
```

---

### 2. `summary` Object

High-level metrics:

```json
"summary": {
  "totalAssertions": 5,              // Total assertions validated
  "supportedCount": 4,               // How many were supported
  "partiallySupportedCount": 0,
  "unsupportedCount": 0,
  "unknownCount": 1,
  "validationScore": 0.80,           // Overall validation quality score
  "averageConfidence": 0.758,        // Average confidence across assertions
  "totalEvidencePieces": 26          // Total evidence found by PATH A
}
```

---

### 3. `validationResults` Array

Detailed results for each assertion validated by PATH A:

```json
[
  {
    "assertion": {
      "id": "assert-llm-6d4408be-6818",
      "text": "The application loaded 166,350 targets into memory causing heap exhaustion",
      "type": "CAUSALITY",           // OBSERVATION | CAUSALITY | CONFIGURATION | RECOMMENDATION
      "source": "ROOT_CAUSE",        // Where extracted from RCA
      "relatedField": "rootCause"
    },
    "status": "SUPPORTED",           // Result of validating this assertion
    "confidence": 0.88,              // Confidence in this validation
    "supportingEvidence": [          // Evidence that supports this claim
      {
        "source": "POD LOGS",
        "type": "POD_LOG",
        "snippet": "Inserted 166000 targets. Current registry size=166001\nScrape started. targets=166324",
        "relevanceScore": 0.95,      // How relevant is this evidence? (0.0-1.0)
        "structuredData": null
      },
      {
        "source": "POD LOGS",
        "type": "POD_LOG",
        "snippet": "Aborting due to java.lang.OutOfMemoryError: Java heap space",
        "relevanceScore": 0.95
      },
      ...
    ],
    "refutingEvidence": [],          // Evidence that contradicts this claim (usually empty)
    "explanation": "The assertion claims '166,350 targets loaded into memory causing heap exhaustion.' The evidence directly supports this: log line shows 'targets=166350' and 'OutOfMemoryError: Java heap space'. Temporal correlation is strong...",
    "validated": true
  },
  ...
]
```

---

### 4. `validatedAt`

ISO-8601 timestamp of when validation completed:

```json
"validatedAt": "2026-07-08T11:33:22.610485967Z"
```

---

## Field Deduplication

Some fields appear in multiple places for convenience:

### Duplicated Fields (Can Remove):

1. **`status` appears 3 times:**
   - `assertionBasedVerdict.status`
   - `ruleBasedVerdict.status`
   - `finalVerdict.status` ← **Keep this (it's the final answer)**

2. **`confidence` appears 3 times:**
   - `assertionBasedVerdict.confidence`
   - `ruleBasedVerdict.confidence`
   - `finalVerdict.confidence` ← **Keep this (it's the final answer)**

3. **Assertion counts appear twice:**
   - `assertionBasedVerdict.{supportedAssertions, ...}`
   - `summary.{supportedCount, ...}` ← **Keep this (summary is the canonical source)**

### Minimal JSON (Deduplicated):

If you want a minimal version for database storage:

```json
{
  "finalVerdict": {
    "status": "SUPPORTED",
    "confidence": 0.83,
    "pathAConfidence": 0.76,
    "pathBConfidence": 0.89
  },
  "assertionsSummary": {
    "total": 5,
    "supported": 4,
    "unknown": 1
  },
  "rulesSummary": {
    "hypothesis": "OOMKilled",
    "totalScore": 14,
    "maxScore": 26,
    "normalizedScore": 0.538,
    "requiredPassed": "4/4",
    "supportingMatched": 2
  },
  "validatedAt": "2026-07-08T11:33:22Z"
}
```

---

## Normalization Recommendation

I recommend **adding these fields** to `ruleBasedVerdict`:

```json
"ruleBasedVerdict": {
  "hypothesis": "OOMKilled",
  "status": "SUPPORTED",
  "confidence": 0.89,
  "totalScore": 14,                  // Keep (shows actual points)
  "maxPossibleScore": 26,            // NEW - for context
  "normalizedScore": 0.538,          // NEW - totalScore / maxPossibleScore
  "scoreBreakdown": {                // NEW - transparency
    "requiredScore": 4,
    "supportingScore": 10,
    "exclusionScore": 0
  },
  ...
}
```

This way:
- **`totalScore`**: Shows absolute points for debugging
- **`normalizedScore`**: Enables cross-hypothesis comparison
- **`maxPossibleScore`**: Provides context for interpretation
- **`scoreBreakdown`**: Shows contribution of each rule type

Would you like me to implement the normalization changes?
