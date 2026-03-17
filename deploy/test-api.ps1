# ID Generator API Test Script

Write-Host "Testing ID Generator Service..." -ForegroundColor Cyan
Write-Host ""

# Test Health Check
Write-Host "1. Health Check:" -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8011/api/v1/id/health"
    Write-Host "   Status: $($health.data.status)" -ForegroundColor Green
    Write-Host "   Snowflake WorkerId: $($health.data.snowflake.workerId)" -ForegroundColor Green
    Write-Host "   Segment BizTags: $($health.data.segment.bizTagCount)" -ForegroundColor Green
} catch {
    Write-Host "   Failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test Snowflake ID Generation
Write-Host "2. Generate Snowflake ID:" -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "http://localhost:8011/api/v1/id/snowflake"
    Write-Host "   Generated ID: $($result.data)" -ForegroundColor Green
} catch {
    Write-Host "   Failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test Batch Snowflake ID Generation
Write-Host "3. Generate Batch Snowflake IDs (5):" -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "http://localhost:8011/api/v1/id/snowflake/batch?count=5"
    Write-Host "   Generated IDs:" -ForegroundColor Green
    $result.data | ForEach-Object { Write-Host "     - $_" -ForegroundColor Green }
} catch {
    Write-Host "   Failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test Segment ID Generation
Write-Host "4. Generate Segment ID (default):" -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "http://localhost:8011/api/v1/id/segment/default"
    Write-Host "   Generated ID: $($result.data)" -ForegroundColor Green
} catch {
    Write-Host "   Failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test Worker Info
Write-Host "5. Get Worker Info:" -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "http://localhost:8011/api/v1/id/snowflake/info"
    Write-Host "   Worker ID: $($result.data.workerId)" -ForegroundColor Green
    Write-Host "   Datacenter ID: $($result.data.datacenterId)" -ForegroundColor Green
    Write-Host "   Initialized: $($result.data.initialized)" -ForegroundColor Green
    Write-Host "   Epoch: $($result.data.epoch)" -ForegroundColor Green
} catch {
    Write-Host "   Failed: $_" -ForegroundColor Red
}
Write-Host ""

Write-Host "All tests completed!" -ForegroundColor Cyan
