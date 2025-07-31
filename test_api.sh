#!/bin/bash

echo "Testing the packing list API..."

# Test with sample data
curl -X POST \
  -F "csvFile=@sample_data.csv" \
  -F "rmb=26" \
  -F "rate=8.00" \
  -F "boxes=18" \
  -F "weight=244.0" \
  -F "manualTracking=1Z12345E1234567890" \
  http://localhost:8080/upload \
  -v \
  -o test_result.zip

echo ""
echo "Response saved to test_result.zip"
echo "File size:"
ls -la test_result.zip

echo ""
echo "Attempting to extract and view results..."
if unzip -t test_result.zip > /dev/null 2>&1; then
    echo "ZIP file is valid, extracting..."
    unzip -o test_result.zip
    echo ""
    echo "=== PACKING LIST CSV ==="
    cat packing-list-*.csv 2>/dev/null || echo "No packing list CSV found"
    echo ""
    echo "=== IMPORT CSV ==="
    cat import_inv-*.csv 2>/dev/null || echo "No import CSV found"
else
    echo "ZIP file is invalid or contains error message:"
    cat test_result.zip
fi
