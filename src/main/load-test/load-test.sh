#!/bin/bash

BASE_URL="http://localhost:8080"
BUCKET="benchmark"
RESULTS_FILE="load-test-results.txt"

echo "=================================" | tee $RESULTS_FILE
echo "  Mini S3 — Load Test Results"    | tee -a $RESULTS_FILE
echo "  $(date)"                         | tee -a $RESULTS_FILE
echo "=================================" | tee -a $RESULTS_FILE

# Create test bucket
curl -s -X PUT "$BASE_URL/buckets/$BUCKET" > /dev/null
echo "✅ Bucket created: $BUCKET"

# Create test files
echo "Creating test files..."
dd if=/dev/urandom of=/tmp/test-1kb.bin  bs=1K  count=1  2>/dev/null
dd if=/dev/urandom of=/tmp/test-1mb.bin  bs=1M  count=1  2>/dev/null
dd if=/dev/urandom of=/tmp/test-10mb.bin bs=1M  count=10 2>/dev/null

echo ""
echo "── Test 1: 100 sequential 1KB uploads ──" | tee -a $RESULTS_FILE
START=$(date +%s%3N)
for i in $(seq 1 100); do
  curl -s -X PUT "$BASE_URL/$BUCKET/file-$i.bin" \
    -F "file=@/tmp/test-1kb.bin" > /dev/null
done
END=$(date +%s%3N)
DURATION=$((END - START))
RPS=$(echo "scale=1; 100000 / $DURATION" | bc)
echo "  Duration: ${DURATION}ms | Throughput: ${RPS} req/s" | tee -a $RESULTS_FILE

echo ""
echo "── Test 2: 100 sequential downloads ──" | tee -a $RESULTS_FILE
START=$(date +%s%3N)
for i in $(seq 1 100); do
  curl -s "$BASE_URL/$BUCKET/file-$i.bin" > /dev/null
done
END=$(date +%s%3N)
DURATION=$((END - START))
RPS=$(echo "scale=1; 100000 / $DURATION" | bc)
echo "  Duration: ${DURATION}ms | Throughput: ${RPS} req/s" | tee -a $RESULTS_FILE

echo ""
echo "── Test 3: 50 concurrent uploads ──" | tee -a $RESULTS_FILE
START=$(date +%s%3N)
for i in $(seq 1 50); do
  curl -s -X PUT "$BASE_URL/$BUCKET/concurrent-$i.bin" \
    -F "file=@/tmp/test-1mb.bin" > /dev/null &
done
wait
END=$(date +%s%3N)
DURATION=$((END - START))
echo "  50 concurrent 1MB uploads in ${DURATION}ms" | tee -a $RESULTS_FILE
MBPS=$(echo "scale=1; 50 / ($DURATION / 1000)" | bc)
echo "  Throughput: ${MBPS} files/s" | tee -a $RESULTS_FILE

echo ""
echo "── Test 4: Cache hit rate ──" | tee -a $RESULTS_FILE
# Upload once
curl -s -X PUT "$BASE_URL/$BUCKET/cache-test.bin" \
  -F "file=@/tmp/test-1kb.bin" > /dev/null
# Download 100 times — should all be cache hits
START=$(date +%s%3N)
for i in $(seq 1 100); do
  curl -s "$BASE_URL/$BUCKET/cache-test.bin" > /dev/null
done
END=$(date +%s%3N)
DURATION=$((END - START))
AVG=$((DURATION / 100))
echo "  100 downloads of same object: avg ${AVG}ms each" | tee -a $RESULTS_FILE

echo ""
echo "── Test 5: Metadata lookup latency ──" | tee -a $RESULTS_FILE
METRICS=$(curl -s "$BASE_URL/admin/metrics")
echo "  $METRICS" | python3 -c "
import sys, json
data = json.load(sys.stdin)
latency = data.get('latency', {})
cache = data.get('cache', {})
print(f'  Upload P99:    {latency.get(\"uploadP99Ms\", \"N/A\")} ms')
print(f'  Download P99:  {latency.get(\"downloadP99Ms\", \"N/A\")} ms')
print(f'  Metadata P99:  {latency.get(\"metadataCacheP99Ms\", \"N/A\")} ms')
print(f'  Cache hit rate: {cache.get(\"hitRate\", \"N/A\")}%')
" 2>/dev/null | tee -a $RESULTS_FILE

echo ""
echo "=================================" | tee -a $RESULTS_FILE
echo "Full results saved to: $RESULTS_FILE"