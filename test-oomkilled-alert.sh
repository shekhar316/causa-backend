#!/bin/bash

# Test script to fire a realistic OOMKilled alert with proper Kubernetes context
# This will trigger the full validation pipeline with PATH A + PATH B

ROUTE_URL=$(oc get route causa-backend -n pinky -o jsonpath='{.spec.host}' 2>/dev/null)

if [ -z "$ROUTE_URL" ]; then
  echo "Error: Could not get causa-backend route"
  exit 1
fi

echo "=== Firing OOMKilled Alert with Full Context ==="
echo "Route: https://$ROUTE_URL"
echo ""

curl -k -X POST "https://$ROUTE_URL/api/v1/webhooks/alerts" \
  -H "Content-Type: application/json" \
  -d '{
  "alerts": [{
    "status": "firing",
    "labels": {
      "alertname": "PodOOMKilled",
      "severity": "critical",
      "pod": "memory-hog-app-7d9f8b6c5-xk2mn",
      "namespace": "production",
      "container": "memory-hog"
    },
    "annotations": {
      "summary": "Pod was OOMKilled due to memory limit exceeded",
      "description": "Container memory-hog in pod memory-hog-app-7d9f8b6c5-xk2mn was OOMKilled. Exit Code: 137. Reason: OOMKilled. Pod Status: CrashLoopBackOff. Memory usage was increasing steadily before termination. Heap usage reached 98% before OOM error."
    }
  }]
}'

echo ""
echo ""
echo "=== Alert fired! Check logs for validation pipeline ==="
echo "oc logs deployment/causa-backend -n pinky --tail=500 -f | grep -E 'PATH|assertion|hypothesis|validation|dual'"
