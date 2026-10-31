$ErrorActionPreference = "Stop"
Write-Host "=== TEST 1: Public Auth Info ==="
$info = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/info"
Write-Host "Site:" $info.site "Registration:" $info.registration

Write-Host "`n=== TEST 2: Admin Login & Dashboard ==="
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLoginBody = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $adminLoginBody -ContentType "application/json" -WebSession $session
Write-Host "Admin logged in:" $adminRes.username "Role:" $adminRes.role "Redirect:" $adminRes.redirect

$dash = Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard" -WebSession $session
Write-Host "Dashboard Points:" $dash.pointCount "Waste on record:" $dash.totalWasteKg "kg Active fleet:" $dash.activeVehicles "DB Health:" $dash.systemHealth.databaseConnected

Write-Host "`n=== TEST 3: Depot Management ==="
$depot = Invoke-RestMethod -Uri "http://localhost:8080/api/depot" -WebSession $session
Write-Host "Current Depot:" $depot.name "Coords:" $depot.latitude "," $depot.longitude
$updateDepotBody = @{ name = "Marudham Central Municipal Yard"; address = "Central Ward Main Yard"; latitude = 8.7960; longitude = 78.1330 } | ConvertTo-Json
$depotUpdated = Invoke-RestMethod -Uri "http://localhost:8080/api/depot" -Method PUT -Body $updateDepotBody -ContentType "application/json" -WebSession $session
Write-Host "Updated Depot:" $depotUpdated.name

Write-Host "`n=== TEST 4: Citizen Login & Private Request Creation ==="
$citizenSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$citLoginBody = @{ username = "citizen1"; password = "citizen123" } | ConvertTo-Json
$citRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $citLoginBody -ContentType "application/json" -WebSession $citizenSession
Write-Host "Citizen logged in:" $citRes.username "Role:" $citRes.role

$reqBody = @{ pointId = "CP02"; wasteKg = 320.0; priority = "HIGH"; wasteType = "ORGANIC"; priorityReason = "Bus stand overflow report" } | ConvertTo-Json
$newReq = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -Method POST -Body $reqBody -ContentType "application/json" -WebSession $citizenSession
Write-Host "Created Request ID:" $newReq.id "Location:" $newReq.pointName "Priority:" $newReq.priority "User:" $newReq.username

$citReqs = Invoke-RestMethod -Uri "http://localhost:8080/api/requests" -WebSession $citizenSession
Write-Host "Citizen only sees their own requests. Total count for citizen1:" $citReqs.Count

Write-Host "`n=== TEST 5: Admin Optimizer & Route Dispatch ==="
$optBody = @{
    requestIds = @($newReq.id)
    truckIds = @("T1")
    startType = "DEPOT"
} | ConvertTo-Json
$optRes = Invoke-RestMethod -Uri "http://localhost:8080/api/optimizer/plan" -Method POST -Body $optBody -ContentType "application/json" -WebSession $session
Write-Host "Routes created:" $optRes.createdRoutes.Count "Assigned Requests:" ($optRes.assignedRequestIds -join ",") "Method:" $optRes.createdRoutes[0].routingMethod
$createdRoute = $optRes.createdRoutes[0]
Write-Host "Route #" $createdRoute.id "Name:" $createdRoute.name "Distance:" $createdRoute.totalDistanceKm "km Stops:" $createdRoute.stops.Count

Write-Host "`n=== TEST 6: Driver Workflow & Stop Completion ==="
$driverSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$driverLoginBody = @{ username = "driver1"; password = "driver123" } | ConvertTo-Json
$driverRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $driverLoginBody -ContentType "application/json" -WebSession $driverSession
Write-Host "Driver logged in:" $driverRes.username "Role:" $driverRes.role

$assign = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/my-assignment" -WebSession $driverSession
Write-Host "Driver Assigned Truck:" $assign.truck.name "Active Route:" $assign.route.name "Next Stop:" $assign.nextStop.pointName

# Report GPS
$gpsBody = @{ lat = 8.7962; lng = 78.1332; source = "DEVICE_GPS" } | ConvertTo-Json
$gpsRes = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/report-location" -Method POST -Body $gpsBody -ContentType "application/json" -WebSession $driverSession
Write-Host "Driver GPS reported:" $gpsRes.success "Source:" $gpsRes.source

# Start Route
$startRes = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/start-route" -Method POST -WebSession $driverSession
Write-Host "Route started status:" $startRes.status

# Collect stop
$stopId = $assign.nextStop.id
$collectBody = @{ actualKg = 320.0; notes = "Fully collected from Bus Stand" } | ConvertTo-Json
$collectRes = Invoke-RestMethod -Uri "http://localhost:8080/api/driver/stop/$stopId/collect" -Method POST -Body $collectBody -ContentType "application/json" -WebSession $driverSession
Write-Host "Stop collected:" $collectRes.success "Route Completed:" $collectRes.routeCompleted "Message:" $collectRes.message

Write-Host "`n=== TEST 7: Analytics & Reports API ==="
$overTime = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/waste-over-time" -WebSession $session
Write-Host "Waste over time labels count:" $overTime.labels.Count "Values sum:" ($overTime.values | Measure-Object -Sum).Sum

$reqStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/requests-by-status" -WebSession $session
Write-Host "Requests by status:" ($reqStatus.labels -join ", ") "Values:" ($reqStatus.values -join ", ")

$csvRoutes = Invoke-RestMethod -Uri "http://localhost:8080/api/reports/routes/export-csv" -WebSession $session
Write-Host "Routes CSV preview (first line):" ($csvRoutes -split "`n")[0]

$auditLogs = Invoke-RestMethod -Uri "http://localhost:8080/api/reports/audit" -WebSession $session
Write-Host "Recent Audit events logged in MySQL:" $auditLogs.Count
Write-Host "Latest audit event:" $auditLogs[0].eventType "-" $auditLogs[0].description
