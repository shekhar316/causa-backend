#!/bin/bash

# Test script to fire realistic heap OOM alert with comprehensive diagnostic context
# This will trigger the full validation pipeline with real OOM data

ROUTE_URL=$(oc get route causa-backend -n pinky -o jsonpath='{.spec.host}' 2>/dev/null)

if [ -z "$ROUTE_URL" ]; then
  echo "Error: Could not get causa-backend route"
  exit 1
fi

echo "=== Firing Heap OOM Alert with Full Diagnostic Context ==="
echo "Route: https://$ROUTE_URL"
echo "Pod: heap-oom-prom-8554b846d7-v5hj2"
echo "Issue: Java heap space exhaustion (166K targets loaded)"
echo ""

curl -k -X POST "https://$ROUTE_URL/api/v1/webhooks/alerts" \
  -H "Content-Type: application/json" \
  -d '{
  "alerts": [{
    "status": "firing",
    "labels": {
      "alertname": "JavaHeapOOM",
      "severity": "critical",
      "pod": "heap-oom-prom-8554b846d7-v5hj2",
      "namespace": "chaos-test",
      "container": "heap-oom-prom"
    },
    "annotations": {
      "summary": "Java application crashed due to OutOfMemoryError: Java heap space",
      "description": "Pod heap-oom-prom-8554b846d7-v5hj2 crashed with OutOfMemoryError. Registry grew to 166K targets. Memory: 478/512 MiB (93%). CPU: 0.421/0.500. Container in BackOff restart loop. JFR shows Serial GC with increasing pause times (82ms → 313ms → 597ms). Kruize recommends memory increase to 806 MiB."
    }
  }]
}'

echo ""
echo ""
echo "=== Alert fired! Monitor validation pipeline ==="
echo "oc logs deployment/causa-backend -n pinky --tail=500 -f | grep -E 'PATH|assertion|hypothesis|validation|dual|OOM'"
