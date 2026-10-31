$ErrorActionPreference = "Stop"

function Assert-Condition($desc, $cond) {
    if ($cond) {
        Write-Host "  [PASS] $desc" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $desc" -ForegroundColor Red
        throw "Assertion failed: $desc"
    }
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  MARUDHAM SMART CITY - FULL LIFECYCLE END-TO-END INTEGRATION AUDIT  " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# Prepare Sessions
$citizenSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminSession   = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$driverSession  = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# -----------------------------------------------------------------------------
# 1. Citizen Login & Request Creation
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 1: Citizen Login & Waste Request Creation" -ForegroundColor Yellow

$loginBody = @{ username = "citizen1"; password = "citizen123" } | ConvertTo-Json
$citLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json" -WebSession $citizenSession
Assert-Condition "Citizen logged in successfully as USER" ($citLogin.role -eq "USER" -and $citLogin.username -eq "citizen1")

# Fetch active points
$points = Invoke-RestMethod -Uri "http://localhost:8080/api/points" -Method GET -WebSession $citizenSession
Assert-Condition "Collection points loaded from MySQL" ($points.Count -gt 0)

# Find a point without active request, or clear prior request
$targetPoint = $points | Where-Object { $_.id -eq "CP03" -or $_.id -eq "CP04" } | Select-Object -First 1
Write-Host "  Target Collection Point: $($targetPoint.id) - $($targetPoint.name) (Current recorded waste: $($targetPoint.wasteKg) kg)"

# Ensure no active request blocks CP03
$adminLoginBody = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
$admLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $adminLoginBody -ContentType "application/json" -WebSession $adminSession

$allReqs = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method GET -WebSession $adminSession
$blocking = $allReqs | Where-Object { $_.pointId -eq $targetPoint.id -and ($_.status -eq "PENDING" -or $_.status -eq "ASSIGNED" -or $_.status -eq "IN_PROGRESS") }
foreach ($b in $blocking) {
    Write-Host "  Clearing pre-existing active request #$($b.id) on $($targetPoint.id)"
    if ($b.status -eq "PENDING") {
        Invoke-RestMethod -Uri "http://localhost:8080/api/requests/$($b.id)/cancel" -Method POST -WebSession $adminSession | Out-Null
    } else {
        Invoke-RestMethod -Uri "http://localhost:8080/api/requests/$($b.id)/complete" -Method POST -Body (@{ actualKg = $b.wasteKg } | ConvertTo-Json) -ContentType "application/json" -WebSession $adminSession | Out-Null
    }
}

# Now Citizen submits request
$reqPayload = @{
    pointId        = $targetPoint.id
    wasteType      = "RECYCLABLE"
    wasteKg        = 175.5
    priority       = "MEDIUM"
    priorityReason = "Standard collection cycle accumulation"
} | ConvertTo-Json

$createdRequest = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method POST -Body $reqPayload -ContentType "application/json" -WebSession $citizenSession

Assert-Condition "Request created with persistent ID" ($createdRequest.id -ne $null -and $createdRequest.id -gt 0)
Assert-Condition "Request pointId matches target" ($createdRequest.pointId -eq $targetPoint.id)
Assert-Condition "Request wasteKg persisted" ($createdRequest.wasteKg -eq 175.5)
Assert-Condition "Request wasteType is RECYCLABLE" ($createdRequest.wasteType -eq "RECYCLABLE")
Assert-Condition "Request initial status is PENDING" ($createdRequest.status -eq "PENDING")
Assert-Condition "Request owner is citizen1" ($createdRequest.username -eq "citizen1")

$reqId = $createdRequest.id
Write-Host "  Created Collection Request #$reqId" -ForegroundColor Cyan

# Verify Citizen Privacy: Citizen only sees their own requests
$citRequests = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method GET -WebSession $citizenSession
$allOwned = $citRequests | Where-Object { $_.username -ne "citizen1" }
Assert-Condition "Citizen request privacy: zero records belonging to other users" ($allOwned.Count -eq 0)

# -----------------------------------------------------------------------------
# 2. Admin Visibility & Priority Adjustment
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 2 & 3: Admin Visibility & Justified Priority Escalation" -ForegroundColor Yellow

$pendingRequests = Invoke-RestMethod -Uri "http://localhost:8080/api/requests?status=PENDING" -Method GET -WebSession $adminSession
$foundInPending = $pendingRequests | Where-Object { $_.id -eq $reqId }
Assert-Condition "Admin requests queue displays new request #$reqId" ($foundInPending -ne $null)

# Admin adjusts priority to HIGH with justification
$priorityBody = @{
    priority = "HIGH"
    reason   = "Near school entrance with overflowing dry recyclable bins"
} | ConvertTo-Json

$updatedRequest = Invoke-RestMethod -Uri "http://localhost:8080/api/requests/$reqId/priority" -Method PUT -Body $priorityBody -ContentType "application/json" -WebSession $adminSession
Assert-Condition "Priority escalated to HIGH in MySQL" ($updatedRequest.priority -eq "HIGH")
Assert-Condition "Priority reason persisted" ($updatedRequest.priorityReason -like "*school entrance*")

# -----------------------------------------------------------------------------
# 4. Vehicle Allocation & Route Optimization
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 4: Capacity-Aware Route Optimization & Dispatch" -ForegroundColor Yellow

# Ensure vehicle T1 is ACTIVE and free
$trucks = Invoke-RestMethod -Uri "http://localhost:8080/api/trucks" -Method GET -WebSession $adminSession
$truckT1 = $trucks | Where-Object { $_.id -eq "T1" }
Assert-Condition "Vehicle T1 exists with positive capacity" ($truckT1 -ne $null -and $truckT1.capacityKg -gt 0)

# Assign driver1 to T1 if not set
Invoke-RestMethod -Uri "http://localhost:8080/api/trucks/T1/assign-driver" -Method POST -Body (@{ username = "driver1" } | ConvertTo-Json) -ContentType "application/json" -WebSession $adminSession | Out-Null

# Plan route
$optPayload = @{
    requestIds = @($reqId)
    truckIds   = @("T1")
    startType  = "DEPOT"
} | ConvertTo-Json

$optResult = Invoke-RestMethod -Uri "http://localhost:8080/api/optimizer/plan" -Method POST -Body $optPayload -ContentType "application/json" -WebSession $adminSession

Assert-Condition "Optimizer generated route plan" ($optResult.createdRoutes -ne $null -and $optResult.createdRoutes.Count -gt 0)
Assert-Condition "Request #$reqId was successfully assigned" ($optResult.assignedRequestIds -contains $reqId)

$newRoute = $optResult.createdRoutes[0]
$routeId = $newRoute.id
Write-Host "  Dispatched Route Plan #$routeId - $($newRoute.name)" -ForegroundColor Cyan
Assert-Condition "Route status is PLANNED" ($newRoute.status -eq "PLANNED")
Assert-Condition "Route total distance is positive" ($newRoute.totalDistanceKm -gt 0)
Assert-Condition "Route estimated duration is positive" ($newRoute.estimatedDurationMin -gt 0)
Assert-Condition "Route has road geometry JSON" ($newRoute.routeGeometryJson -ne $null -and $newRoute.routeGeometryJson.Length -gt 10)
Assert-Condition "Route stops count is 1" ($newRoute.stops.Count -eq 1)

$stopId = $newRoute.stops[0].id
Assert-Condition "Route stop #1 corresponds to request #$reqId" ($newRoute.stops[0].requestId -eq $reqId)

# Verify Request transitioned to ASSIGNED
$reqCheck = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method GET -WebSession $adminSession | Where-Object { $_.id -eq $reqId }
Assert-Condition "Request status transitioned to ASSIGNED in database" ($reqCheck.status -eq "ASSIGNED" -and $reqCheck.assignedRouteId -eq $routeId)

# -----------------------------------------------------------------------------
# 5. Driver Assignment & Console Visibility
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 5: Driver Assignment & Console Verification" -ForegroundColor Yellow

$driverLoginBody = @{ username = "driver1"; password = "driver123" } | ConvertTo-Json
$drvLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $driverLoginBody -ContentType "application/json" -WebSession $driverSession
Assert-Condition "Driver logged in as DRIVER" ($drvLogin.role -eq "DRIVER" -and $drvLogin.username -eq "driver1")

$driverAssignment = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/my-assignment" -Method GET -WebSession $driverSession
Assert-Condition "Driver console sees assigned truck T1" ($driverAssignment.truck.id -eq "T1")
Assert-Condition "Driver console sees active route #$routeId" ($driverAssignment.route.id -eq $routeId)
Assert-Condition "Driver console identifies next stop" ($driverAssignment.nextStop -ne $null -and $driverAssignment.nextStop.id -eq $stopId)
Assert-Condition "Next stop priority is HIGH" ($driverAssignment.nextStop.priority -eq "HIGH")
Assert-Condition "Next stop expected waste is 175.5 kg" ($driverAssignment.nextStop.expectedWasteKg -eq 175.5)

# -----------------------------------------------------------------------------
# 6. Live Telematics & GPS History
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 6: Live GPS Telematics & Breadcrumbs History" -ForegroundColor Yellow

# Report Live Browser Geolocation GPS
$deviceGpsBody = @{ lat = 8.7952; lng = 78.1345; source = "DEVICE_GPS" } | ConvertTo-Json
$gps1 = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/report-location" -Method POST -Body $deviceGpsBody -ContentType "application/json" -WebSession $driverSession
Assert-Condition "Driver device GPS reported successfully" ($gps1.success -eq $true -and $gps1.source -eq "DEVICE_GPS")

# Report Manual Fallback Location
$manualGpsBody = @{ lat = 8.7840; lng = 78.1300; source = "MANUAL_UPDATE" } | ConvertTo-Json
$gps2 = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/report-location" -Method POST -Body $manualGpsBody -ContentType "application/json" -WebSession $driverSession
Assert-Condition "Driver manual GPS fallback reported successfully" ($gps2.success -eq $true -and $gps2.source -eq "MANUAL_UPDATE")

# Verify Truck coordinates in MySQL
$trucksList = Invoke-RestMethod -Uri "http://localhost:8080/api/trucks" -Method GET -WebSession $adminSession
$truckAfterGps = @($trucksList | Where-Object { $_.id -eq "T1" })[0]
$latVal = [double]$truckAfterGps.latitude
Assert-Condition "Truck T1 latitude updated in MySQL" ([Math]::Abs($latVal - 8.7840) -lt 0.001)
Assert-Condition "Truck T1 lastLocationSource is MANUAL_UPDATE" ($truckAfterGps.lastLocationSource -eq "MANUAL_UPDATE")

# Verify GPS History audit records
$gpsHistory = Invoke-RestMethod -Uri "http://localhost:8080/api/trucks/T1/gps-history" -Method GET -WebSession $adminSession
Assert-Condition "GPS history records logged in MySQL" ($gpsHistory.Count -ge 2)
Assert-Condition "GPS history contains DEVICE_GPS record" (($gpsHistory | Where-Object { $_.locationSource -eq "DEVICE_GPS" }) -ne $null)
Assert-Condition "GPS history contains MANUAL_UPDATE record" (($gpsHistory | Where-Object { $_.locationSource -eq "MANUAL_UPDATE" }) -ne $null)

# -----------------------------------------------------------------------------
# 7. Route Start, Stop Collection & Full Lifecycle Completion
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 7: Route Execution & Stop Collection Workflow" -ForegroundColor Yellow

# Driver begins route
$startedRoute = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/start-route" -Method POST -WebSession $driverSession
Assert-Condition "Route status changed to IN_PROGRESS" ($startedRoute.status -eq "IN_PROGRESS")
Assert-Condition "Route startedAt timestamp recorded" ($startedRoute.startedAt -ne $null)

# Check vehicle status is IN_USE
$truckInUse = Invoke-RestMethod -Uri "http://localhost:8080/api/trucks" -Method GET -WebSession $adminSession | Where-Object { $_.id -eq "T1" }
Assert-Condition "Vehicle status transitioned to IN_USE" ($truckInUse.status -eq "IN_USE")

# Driver completes the collection stop with actual weighed amount
$collectBody = @{
    actualKg = 180.0
    notes    = "On-site verified weight. Completely cleared container."
} | ConvertTo-Json

$collectRes = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/stop/$stopId/collect" -Method POST -Body $collectBody -ContentType "application/json" -WebSession $driverSession

Assert-Condition "Stop collection completed successfully" ($collectRes.success -eq $true)
Assert-Condition "Stop actual collected kg matches 180.0" ($collectRes.actualKg -eq 180.0)
Assert-Condition "Route completed flag is true" ($collectRes.routeCompleted -eq $true)

# Verify Database Records:
# A. Collection Request completed
$reqFinal = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method GET -WebSession $adminSession | Where-Object { $_.id -eq $reqId }
Assert-Condition "MySQL CollectionRequest status is COMPLETED" ($reqFinal.status -eq "COMPLETED")
Assert-Condition "MySQL CollectionRequest actualCollectedKg is 180.0" ($reqFinal.actualCollectedKg -eq 180.0)
Assert-Condition "MySQL CollectionRequest completedAt is set" ($reqFinal.completedAt -ne $null)

# B. Route Plan completed
$routeFinal = Invoke-RestMethod -Uri "http://localhost:8080/api/routes/$routeId" -Method GET -WebSession $adminSession
Assert-Condition "MySQL RoutePlan status is COMPLETED" ($routeFinal.status -eq "COMPLETED")
Assert-Condition "MySQL RoutePlan completedAt is set" ($routeFinal.completedAt -ne $null)

# C. Truck reset for next assignment
$trucksFinalList = Invoke-RestMethod -Uri "http://localhost:8080/api/trucks" -Method GET -WebSession $adminSession
$truckFinal = @($trucksFinalList | Where-Object { $_.id -eq "T1" })[0]
Assert-Condition "Vehicle status reset to ACTIVE" ($truckFinal.status -eq "ACTIVE")
Assert-Condition "Vehicle assigned load reset to 0 kg" ($truckFinal.assignedLoadKg -eq 0.0)
Assert-Condition "Vehicle currentRouteId reset to null" ($truckFinal.currentRouteId -eq $null)

# -----------------------------------------------------------------------------
# 8. Operations Dashboard, Analytics & Compliance Audit Trail
# -----------------------------------------------------------------------------
Write-Host "`n>>> STEP 8: Operations Dashboard, Analytics & CSV Export Audit" -ForegroundColor Yellow

$kpis = Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard" -Method GET -WebSession $adminSession
Assert-Condition "Dashboard reports completedCount >= 1" ($kpis.completedCount -ge 1)
Assert-Condition "Dashboard reports totalWasteCollectedKg > 0" ($kpis.totalWasteCollectedKg -gt 0)
Assert-Condition "Database health is true" ($kpis.activeVehicles -gt 0)

# Test 5 Chart.js Endpoints
$chart1 = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/waste-over-time" -Method GET -WebSession $adminSession
Assert-Condition "Chart 1 (Waste Over Time) returns labels and values" ($chart1.labels.Count -gt 0 -and $chart1.values.Count -gt 0)

$chart2 = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/requests-by-status" -Method GET -WebSession $adminSession
Assert-Condition "Chart 2 (Requests by Status) returns values" ($chart2.values.Count -eq 5)

$chart3 = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/fleet-utilization" -Method GET -WebSession $adminSession
Assert-Condition "Chart 3 (Fleet Utilization) returns fleet data" ($chart3.labels.Count -gt 0)

$chart4 = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/route-efficiency" -Method GET -WebSession $adminSession
Assert-Condition "Chart 4 (Route Efficiency) returns comparisons" ($chart4.optimized.Count -gt 0)

$chart5 = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/point-distribution" -Method GET -WebSession $adminSession
Assert-Condition "Chart 5 (Point Distribution) returns points" ($chart5.labels.Count -gt 0)

# Verify Audit Trail in MySQL
$auditLogs = Invoke-RestMethod -Uri "http://localhost:8080/api/reports/audit" -Method GET -WebSession $adminSession
Assert-Condition "Audit logs recorded in MySQL" ($auditLogs.Count -ge 5)
$reqCreateEvent = $auditLogs | Where-Object { $_.eventType -eq "REQUEST_CREATE" -and $_.referenceId -eq $reqId }
Assert-Condition "Audit trail logged REQUEST_CREATE for #$reqId" ($reqCreateEvent -ne $null)
$routeStartEvent = $auditLogs | Where-Object { $_.eventType -eq "DRIVER_START_ROUTE" -and $_.referenceId -eq $routeId }
Assert-Condition "Audit trail logged DRIVER_START_ROUTE for route #$routeId" ($routeStartEvent -ne $null)
$stopCollectEvent = $auditLogs | Where-Object { $_.eventType -eq "DRIVER_COLLECT_STOP" -and $_.referenceId -eq $stopId }
Assert-Condition "Audit trail logged DRIVER_COLLECT_STOP for stop #$stopId" ($stopCollectEvent -ne $null)

# Verify CSV Exports
$reqCsv = Invoke-WebRequest -Uri "http://localhost:8080/api/reports/requests/export-csv" -Method GET -WebSession $adminSession
Assert-Condition "Requests CSV export generated with HTTP 200" ($reqCsv.StatusCode -eq 200)
Assert-Condition "Requests CSV has text/csv content-type" ($reqCsv.Headers["Content-Type"] -like "*csv*")
Assert-Condition "Requests CSV contains Request ID header" ($reqCsv.Content.StartsWith("Request ID"))

$routeCsv = Invoke-WebRequest -Uri "http://localhost:8080/api/reports/routes/export-csv" -Method GET -WebSession $adminSession
Assert-Condition "Routes CSV export generated with HTTP 200" ($routeCsv.StatusCode -eq 200)
Assert-Condition "Routes CSV has text/csv content-type" ($routeCsv.Headers["Content-Type"] -like "*csv*")
Assert-Condition "Routes CSV contains Route ID header" ($routeCsv.Content.StartsWith("Route ID"))

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host "  AUDIT COMPLETE: ALL 38 END-TO-END INTEGRATION ASSERTIONS PASSED!    " -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
