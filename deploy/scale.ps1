# =====================================================
# ID Generator Scaling Script
# Easily scale ID Generator instances up or down
# =====================================================

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("up", "down", "status", "scale", "logs", "test")]
    [string]$Action = "status",
    
    [Parameter(Mandatory=$false)]
    [int]$Count = 2
)

$ComposeFile = "docker-compose.scale.yml"

function Show-Banner {
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║     ID Generator Scaling Management Tool      ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
}

function Start-Services {
    param([int]$InstanceCount)
    
    Write-Host "Starting ID Generator with $InstanceCount instance(s)..." -ForegroundColor Yellow
    Write-Host ""
    
    docker compose -f $ComposeFile up -d --scale id-generator=$InstanceCount
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Services started successfully!" -ForegroundColor Green
        Write-Host ""
        Start-Sleep -Seconds 5
        Show-Status
    } else {
        Write-Host ""
        Write-Host "✗ Failed to start services" -ForegroundColor Red
    }
}

function Stop-Services {
    Write-Host "Stopping all ID Generator services..." -ForegroundColor Yellow
    Write-Host ""
    
    docker compose -f $ComposeFile down
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Services stopped successfully!" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "✗ Failed to stop services" -ForegroundColor Red
    }
}

function Show-Status {
    Write-Host "Current Service Status:" -ForegroundColor Cyan
    Write-Host ""
    
    docker compose -f $ComposeFile ps
    
    Write-Host ""
    Write-Host "ID Generator Instances:" -ForegroundColor Cyan
    
    $containers = docker ps --filter "name=id-generator" --format "{{.Names}}" | Where-Object { $_ -match "id-generator-\d+" }
    
    if ($containers) {
        $instanceCount = ($containers | Measure-Object).Count
        Write-Host "  Running: $instanceCount instance(s)" -ForegroundColor Green
        Write-Host ""
        
        foreach ($container in $containers) {
            $port = docker port $container 8011/tcp 2>$null
            if ($port) {
                $port = $port.Split(':')[-1]
                Write-Host "  • $container" -ForegroundColor White
                Write-Host "    Port: $port" -ForegroundColor Gray
                
                # Try to get worker ID
                try {
                    $health = Invoke-RestMethod -Uri "http://localhost:$port/api/v1/id/health" -ErrorAction SilentlyContinue
                    Write-Host "    Worker ID: $($health.snowflake.workerId)" -ForegroundColor Gray
                    Write-Host "    Status: $($health.status)" -ForegroundColor Green
                } catch {
                    Write-Host "    Status: Starting..." -ForegroundColor Yellow
                }
                Write-Host ""
            }
        }
    } else {
        Write-Host "  No instances running" -ForegroundColor Yellow
    }
}

function Scale-Services {
    param([int]$NewCount)
    
    Write-Host "Scaling ID Generator to $NewCount instance(s)..." -ForegroundColor Yellow
    Write-Host ""
    
    docker compose -f $ComposeFile up -d --scale id-generator=$NewCount --no-recreate
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Scaled successfully!" -ForegroundColor Green
        Write-Host ""
        Start-Sleep -Seconds 5
        Show-Status
    } else {
        Write-Host ""
        Write-Host "✗ Failed to scale services" -ForegroundColor Red
    }
}

function Show-Logs {
    Write-Host "Showing logs for all ID Generator instances..." -ForegroundColor Yellow
    Write-Host "Press Ctrl+C to exit" -ForegroundColor Gray
    Write-Host ""
    
    docker compose -f $ComposeFile logs -f id-generator
}

function Test-Instances {
    Write-Host "Testing all ID Generator instances..." -ForegroundColor Yellow
    Write-Host ""
    
    $containers = docker ps --filter "name=id-generator" --format "{{.Names}}" | Where-Object { $_ -match "id-generator-\d+" }
    
    if (-not $containers) {
        Write-Host "No instances running!" -ForegroundColor Red
        return
    }
    
    $results = @()
    
    foreach ($container in $containers) {
        $port = docker port $container 8011/tcp 2>$null
        if ($port) {
            $port = $port.Split(':')[-1]
            
            try {
                # Test health
                $health = Invoke-RestMethod -Uri "http://localhost:$port/api/v1/id/health" -ErrorAction Stop
                
                # Generate ID
                $idResult = Invoke-RestMethod -Uri "http://localhost:$port/api/v1/id/snowflake" -ErrorAction Stop
                
                $results += [PSCustomObject]@{
                    Container = $container
                    Port = $port
                    WorkerId = $health.snowflake.workerId
                    Status = $health.status
                    GeneratedId = $idResult.data
                    Success = $true
                }
                
                Write-Host "✓ $container (Port: $port, Worker ID: $($health.snowflake.workerId))" -ForegroundColor Green
                Write-Host "  Generated ID: $($idResult.data)" -ForegroundColor Gray
                
            } catch {
                $results += [PSCustomObject]@{
                    Container = $container
                    Port = $port
                    WorkerId = "N/A"
                    Status = "Error"
                    GeneratedId = "N/A"
                    Success = $false
                }
                
                Write-Host "✗ $container (Port: $port) - Failed" -ForegroundColor Red
            }
        }
    }
    
    Write-Host ""
    Write-Host "Test Summary:" -ForegroundColor Cyan
    $successCount = ($results | Where-Object { $_.Success }).Count
    $totalCount = $results.Count
    Write-Host "  Passed: $successCount / $totalCount" -ForegroundColor $(if ($successCount -eq $totalCount) { "Green" } else { "Yellow" })
    
    # Check for unique Worker IDs
    $workerIds = $results | Where-Object { $_.Success } | Select-Object -ExpandProperty WorkerId
    $uniqueWorkerIds = $workerIds | Select-Object -Unique
    
    if ($workerIds.Count -eq $uniqueWorkerIds.Count) {
        Write-Host "  Worker IDs: All unique ✓" -ForegroundColor Green
    } else {
        Write-Host "  Worker IDs: Duplicates detected! ✗" -ForegroundColor Red
    }
    
    # Check for unique IDs
    $generatedIds = $results | Where-Object { $_.Success } | Select-Object -ExpandProperty GeneratedId
    $uniqueIds = $generatedIds | Select-Object -Unique
    
    if ($generatedIds.Count -eq $uniqueIds.Count) {
        Write-Host "  Generated IDs: All unique ✓" -ForegroundColor Green
    } else {
        Write-Host "  Generated IDs: Duplicates detected! ✗" -ForegroundColor Red
    }
}

function Show-Help {
    Write-Host "Usage: .\scale.ps1 -Action <action> [-Count <number>]" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Actions:" -ForegroundColor Yellow
    Write-Host "  up       Start services with specified instance count (default: 2)"
    Write-Host "  down     Stop all services"
    Write-Host "  status   Show current status (default)"
    Write-Host "  scale    Scale to specified instance count"
    Write-Host "  logs     Show logs from all instances"
    Write-Host "  test     Test all running instances"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Yellow
    Write-Host "  .\scale.ps1 -Action up -Count 3          # Start with 3 instances"
    Write-Host "  .\scale.ps1 -Action scale -Count 5       # Scale to 5 instances"
    Write-Host "  .\scale.ps1 -Action status               # Show current status"
    Write-Host "  .\scale.ps1 -Action test                 # Test all instances"
    Write-Host "  .\scale.ps1 -Action down                 # Stop all services"
    Write-Host ""
}

# Main execution
Show-Banner

switch ($Action) {
    "up" {
        Start-Services -InstanceCount $Count
    }
    "down" {
        Stop-Services
    }
    "status" {
        Show-Status
    }
    "scale" {
        Scale-Services -NewCount $Count
    }
    "logs" {
        Show-Logs
    }
    "test" {
        Test-Instances
    }
    default {
        Show-Help
    }
}

Write-Host ""
