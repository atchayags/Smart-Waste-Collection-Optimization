package com.marudham.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import marudham.PathResult;
import marudham.RoadNetwork;
import marudham.RoutePlanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RouteOptimizationService {

    @Value("${ors.api.key:}")
    private String orsApiKey;

    private final RoutePlanRepository routePlanRepo;
    private final RouteStopRepository routeStopRepo;
    private final CollectionRequestRepository requestRepo;
    private final CollectionPointRepository pointRepo;
    private final TruckRepository truckRepo;
    private final DepotRepository depotRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();
    private final RoutePlanner routePlanner = new RoutePlanner();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RoadRestrictionRepository roadRestrictionRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RouteRevisionRepository routeRevisionRepo;

    public RouteOptimizationService(RoutePlanRepository routePlanRepo,
                                    RouteStopRepository routeStopRepo,
                                    CollectionRequestRepository requestRepo,
                                    CollectionPointRepository pointRepo,
                                    TruckRepository truckRepo,
                                    DepotRepository depotRepo,
                                    AuditService auditService) {
        this.routePlanRepo = routePlanRepo;
        this.routeStopRepo = routeStopRepo;
        this.requestRepo = requestRepo;
        this.pointRepo = pointRepo;
        this.truckRepo = truckRepo;
        this.depotRepo = depotRepo;
        this.auditService = auditService;
    }

    public static class SegmentDetail {
        public int segmentIndex;
        public String fromName;
        public double fromLat;
        public double fromLng;
        public String toName;
        public double toLat;
        public double toLng;
        public double distanceKm;
        public double drivingTimeMin;
        public double collectionTimeMin;
        public double totalTimeMin;
        public String status = "PENDING"; // PENDING, COMPLETED, IN_PROGRESS

        public SegmentDetail() {}
        public SegmentDetail(int segmentIndex, String fromName, double fromLat, double fromLng,
                             String toName, double toLat, double toLng, double distanceKm,
                             double drivingTimeMin, double collectionTimeMin, double totalTimeMin, String status) {
            this.segmentIndex = segmentIndex;
            this.fromName = fromName;
            this.fromLat = fromLat;
            this.fromLng = fromLng;
            this.toName = toName;
            this.toLat = toLat;
            this.toLng = toLng;
            this.distanceKm = distanceKm;
            this.drivingTimeMin = drivingTimeMin;
            this.collectionTimeMin = collectionTimeMin;
            this.totalTimeMin = totalTimeMin;
            this.status = status;
        }
    }

    public static class OptimizationRequest {
        public List<Long> requestIds;
        public List<String> truckIds;
        public String startType; // "DEPOT", "TRUCK_GPS", "CUSTOM"
        public Double customStartLat;
        public Double customStartLng;
        public String customStartName;
        public Integer maxLocations; // Configurable stop count limit
    }

    public static class RouteStopPreview {
        public int stopOrder;
        public Long requestId;
        public String pointId;
        public String pointName;
        public double latitude;
        public double longitude;
        public double expectedWasteKg;
        public String priority;

        public RouteStopPreview() {}
        public RouteStopPreview(int stopOrder, Long requestId, String pointId, String pointName,
                                double latitude, double longitude, double expectedWasteKg, String priority) {
            this.stopOrder = stopOrder;
            this.requestId = requestId;
            this.pointId = pointId;
            this.pointName = pointName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.expectedWasteKg = expectedWasteKg;
            this.priority = priority;
        }
    }

    public static class UnassignedRequestDetail {
        public Long requestId;
        public String pointName;
        public double wasteKg;
        public String priority;
        public String reason;

        public UnassignedRequestDetail() {}
        public UnassignedRequestDetail(Long requestId, String pointName, double wasteKg, String priority, String reason) {
            this.requestId = requestId;
            this.pointName = pointName;
            this.wasteKg = wasteKg;
            this.priority = priority;
            this.reason = reason;
        }
    }

    public static class OptimizationPreviewResult {
        public boolean success = true;
        public int selectedRequestsCount;
        public int plannedStopsCount;
        public double totalWasteKg;
        public double vehicleCapacityKg;
        public double capacityUtilizationPct;
        public double totalDistanceKm;
        public double estimatedDurationMin;
        public String routingMethod;
        public String routeGeometryJson;
        public String truckId;
        public String truckName;
        public String startLocationName;
        public double startLat;
        public double startLng;
        public String depotName;
        public double depotLat;
        public double depotLng;
        public Map<String, Integer> priorityDistribution = new LinkedHashMap<>();
        public List<RouteStopPreview> plannedStops = new ArrayList<>();
        public List<SegmentDetail> segments = new ArrayList<>();
        public double drivingTimeMin;
        public double collectionTimeMin;
        public String estimatedDepotArrival;
        public int totalStopsCount;
        public int completedStopsCount;
        public int remainingStopsCount;
        public int revisionNumber = 1;
        public String recalculationReason;
        public List<UnassignedRequestDetail> unassignedRequests = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    public static class OptimizationResult {
        public List<RoutePlan> createdRoutes = new ArrayList<>();
        public List<Long> assignedRequestIds = new ArrayList<>();
        public List<Long> unassignedRequestIds = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public String routingMethod;
        public OptimizationPreviewResult preview;
    }

    // -------------------------------------------------------------
    // 1. PREVIEW ROUTE (Non-persisting, non-mutating calculation)
    // -------------------------------------------------------------
    @Transactional(readOnly = true)
    public OptimizationPreviewResult previewRoute(OptimizationRequest req) {
        Depot depot = getOrCreateDepot();
        List<CollectionRequest> eligibleRequests = validateAndFilterRequests(req);
        Truck truck = selectEligibleTruck(req);

        // Map collection points
        Map<String, CollectionPoint> pointMap = loadPointMap(eligibleRequests);

        // Apply configurable stop count if requested
        if (req.maxLocations != null && req.maxLocations > 0 && eligibleRequests.size() > req.maxLocations) {
            eligibleRequests = new ArrayList<>(eligibleRequests.subList(0, req.maxLocations));
        }

        // Capacity and Priority Allocation
        PlannedLoadAllocation alloc = allocateLoadUnderCapacity(truck, eligibleRequests, pointMap);

        // Determine Start Coordinates
        StartCoordinateInfo startInfo = resolveStartLocation(req, truck, depot);

        // Build Road Network Graph & Run Dijkstra Routing
        Map<String, double[]> nodeCoords = new HashMap<>();
        RoadNetwork net = buildMunicipalRoadNetwork(depot, pointMap, startInfo.startNodeId, startInfo.lat, startInfo.lng, nodeCoords);

        // Optimize Stop Order using Dijkstra road distances & 2-Opt
        List<CollectionRequest> orderedStops = orderStopsWithDijkstra(net, startInfo.startNodeId, "DEPOT", alloc.assignedRequests, pointMap);

        // Calculate Road Distance, Travel Duration, and GIS Geometry
        RouteCalculation calc = calculateDijkstraRoadRoute(net, depot, startInfo.startNodeId, startInfo.lat, startInfo.lng, orderedStops, pointMap, nodeCoords);

        // Assemble Preview Result
        OptimizationPreviewResult preview = new OptimizationPreviewResult();
        preview.selectedRequestsCount = (req.requestIds != null) ? req.requestIds.size() : 0;
        preview.plannedStopsCount = orderedStops.size();
        preview.totalWasteKg = Math.round(alloc.assignedTotalWasteKg * 10.0) / 10.0;
        preview.vehicleCapacityKg = truck.getCapacityKg();
        preview.capacityUtilizationPct = Math.round(Math.min(100.0, (preview.totalWasteKg / truck.getCapacityKg()) * 100.0) * 10.0) / 10.0;
        preview.totalDistanceKm = Math.round(calc.distanceKm * 10.0) / 10.0;
        preview.estimatedDurationMin = Math.round(calc.durationMin * 10.0) / 10.0;
        preview.drivingTimeMin = Math.round(calc.drivingTimeMin * 10.0) / 10.0;
        preview.collectionTimeMin = Math.round(calc.collectionTimeMin * 10.0) / 10.0;
        preview.totalStopsCount = orderedStops.size();
        preview.completedStopsCount = 0;
        preview.remainingStopsCount = orderedStops.size();
        preview.estimatedDepotArrival = LocalDateTime.now().plusMinutes((long) Math.ceil(preview.estimatedDurationMin)).format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
        preview.segments = calc.segments;
        preview.routingMethod = calc.method;
        preview.routeGeometryJson = calc.geometryJson;
        preview.truckId = truck.getId();
        preview.truckName = truck.getName();
        preview.startLocationName = startInfo.name;
        preview.startLat = startInfo.lat;
        preview.startLng = startInfo.lng;
        preview.depotName = depot.getName();
        preview.depotLat = depot.getLatitude();
        preview.depotLng = depot.getLongitude();
        preview.priorityDistribution = alloc.priorityCounts;
        preview.unassignedRequests = alloc.unassignedDetails;
        preview.warnings.addAll(alloc.warnings);

        int order = 1;
        for (CollectionRequest cr : orderedStops) {
            CollectionPoint cp = pointMap.get(cr.getPointId());
            preview.plannedStops.add(new RouteStopPreview(
                    order++, cr.getId(), cr.getPointId(), cr.getPointName(),
                    cp.getLatitude(), cp.getLongitude(), cr.getWasteKg(), cr.getPriority()
            ));
        }

        return preview;
    }

    // -------------------------------------------------------------
    // 2. DISPATCH ROUTE (Authoritative persistence in MySQL)
    // -------------------------------------------------------------
    @Transactional
    public OptimizationResult planAndDispatch(OptimizationRequest req, String username) {
        OptimizationResult result = new OptimizationResult();
        Depot depot = getOrCreateDepot();

        List<CollectionRequest> eligibleRequests = validateAndFilterRequests(req);
        Truck truck = selectEligibleTruck(req);
        Map<String, CollectionPoint> pointMap = loadPointMap(eligibleRequests);

        if (req.maxLocations != null && req.maxLocations > 0 && eligibleRequests.size() > req.maxLocations) {
            eligibleRequests = new ArrayList<>(eligibleRequests.subList(0, req.maxLocations));
        }

        PlannedLoadAllocation alloc = allocateLoadUnderCapacity(truck, eligibleRequests, pointMap);
        result.warnings.addAll(alloc.warnings);
        for (UnassignedRequestDetail u : alloc.unassignedDetails) {
            result.unassignedRequestIds.add(u.requestId);
            result.warnings.add("Request #" + u.requestId + " (" + u.pointName + ") unassigned: " + u.reason);
        }

        if (alloc.assignedRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No requests could be assigned within vehicle capacity (" + truck.getCapacityKg() + " kg).");
        }

        StartCoordinateInfo startInfo = resolveStartLocation(req, truck, depot);

        // Build Road Network & Run Dijkstra
        Map<String, double[]> nodeCoords = new HashMap<>();
        RoadNetwork net = buildMunicipalRoadNetwork(depot, pointMap, startInfo.startNodeId, startInfo.lat, startInfo.lng, nodeCoords);

        List<CollectionRequest> orderedStops = orderStopsWithDijkstra(net, startInfo.startNodeId, "DEPOT", alloc.assignedRequests, pointMap);
        RouteCalculation calc = calculateDijkstraRoadRoute(net, depot, startInfo.startNodeId, startInfo.lat, startInfo.lng, orderedStops, pointMap, nodeCoords);

        double totalWaste = orderedStops.stream().mapToDouble(CollectionRequest::getWasteKg).sum();
        double utilPct = Math.min(100.0, (totalWaste / truck.getCapacityKg()) * 100.0);

        RoutePlan plan = new RoutePlan(
                "Route for " + truck.getName() + " (" + orderedStops.size() + " stops)",
                truck.getId(),
                truck.getName(),
                startInfo.lat,
                startInfo.lng,
                depot.getLatitude(),
                depot.getLongitude(),
                Math.round(calc.distanceKm * 10.0) / 10.0,
                Math.round(calc.durationMin * 10.0) / 10.0,
                Math.round(totalWaste * 10.0) / 10.0,
                Math.round(utilPct * 10.0) / 10.0,
                calc.method,
                calc.geometryJson
        );
        plan.setStartLocationName(startInfo.name);
        plan.setDepotName(depot.getName());
        plan.setAssignedDriverUsername(truck.getAssignedDriverUsername());
        try {
            plan.setSegmentDetailsJson(objectMapper.writeValueAsString(calc.segments));
        } catch (Exception ignored) {}

        int order = 1;
        for (CollectionRequest cr : orderedStops) {
            CollectionPoint cp = pointMap.get(cr.getPointId());
            RouteStop stop = new RouteStop(
                    order++,
                    cr.getId(),
                    cr.getPointId(),
                    cr.getPointName(),
                    cp.getLatitude(),
                    cp.getLongitude(),
                    cr.getWasteKg(),
                    cr.getPriority()
            );
            plan.addStop(stop);
        }

        RoutePlan savedPlan = routePlanRepo.save(plan);

        // Link requests to saved route and set status to ASSIGNED
        for (CollectionRequest cr : orderedStops) {
            cr.setStatus("ASSIGNED");
            cr.setAssignedRouteId(savedPlan.getId());
            requestRepo.save(cr);
            result.assignedRequestIds.add(cr.getId());
        }

        // Update truck
        truck.setAssignedLoadKg(Math.round(totalWaste * 10.0) / 10.0);
        truck.setCurrentRouteId(savedPlan.getId());
        truckRepo.save(truck);

        result.createdRoutes.add(savedPlan);
        result.routingMethod = calc.method;

        auditService.record("ROUTE_DISPATCH", "Dispatched " + savedPlan.getName() + " with " + orderedStops.size() + " stops (" + totalWaste + " kg, " + calc.method + ")", username, savedPlan.getId());

        return result;
    }

    // -------------------------------------------------------------
    // VALIDATION & ALLOCATION HELPERS
    // -------------------------------------------------------------
    private Depot getOrCreateDepot() {
        return depotRepo.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(() -> depotRepo.save(new Depot("Marudham Central Depot", "Central Municipal Yard", 8.7960, 78.1330)));
    }

    private List<CollectionRequest> validateAndFilterRequests(OptimizationRequest req) {
        if (req.requestIds == null || req.requestIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one collection request to optimize.");
        }
        List<CollectionRequest> candidateRequests = requestRepo.findAllById(req.requestIds);
        List<CollectionRequest> eligible = new ArrayList<>();
        for (CollectionRequest r : candidateRequests) {
            if ("PENDING".equals(r.getStatus())) {
                eligible.add(r);
            }
        }
        if (eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "None of the selected requests are in PENDING status.");
        }
        return eligible;
    }

    private Truck selectEligibleTruck(OptimizationRequest req) {
        if (req.truckIds != null && !req.truckIds.isEmpty()) {
            for (String tid : req.truckIds) {
                Optional<Truck> opt = truckRepo.findById(tid);
                if (opt.isPresent()) {
                    Truck t = opt.get();
                    if ("ACTIVE".equalsIgnoreCase(t.getStatus())) {
                        return t;
                    }
                    // Self-healing: if truck has no active route or previous route is completed/cancelled, restore to ACTIVE
                    if ("IN_USE".equalsIgnoreCase(t.getStatus()) && t.getCurrentRouteId() == null) {
                        t.setStatus("ACTIVE");
                        truckRepo.save(t);
                        return t;
                    }
                    if (t.getCurrentRouteId() != null) {
                        Optional<RoutePlan> rp = routePlanRepo.findById(t.getCurrentRouteId());
                        if (rp.isPresent() && ("COMPLETED".equals(rp.get().getStatus()) || "CANCELLED".equals(rp.get().getStatus()))) {
                            t.setStatus("ACTIVE");
                            t.setCurrentRouteId(null);
                            t.setAssignedLoadKg(0.0);
                            truckRepo.save(t);
                            return t;
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle " + t.getId() + " is currently " + t.getStatus() + " (not eligible for dispatch).");
                }
            }
        }
        List<Truck> activeTrucks = truckRepo.findByStatus("ACTIVE");
        if (activeTrucks.isEmpty()) {
            for (Truck t : truckRepo.findAll()) {
                if (t.getCurrentRouteId() == null && !"BREAKDOWN".equalsIgnoreCase(t.getStatus()) && !"MAINTENANCE".equalsIgnoreCase(t.getStatus())) {
                    t.setStatus("ACTIVE");
                    truckRepo.save(t);
                    return t;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active vehicles available for route assignment.");
        }
        return activeTrucks.get(0);
    }

    private Map<String, CollectionPoint> loadPointMap(List<CollectionRequest> requests) {
        Map<String, CollectionPoint> pointMap = new HashMap<>();
        for (CollectionRequest cr : requests) {
            pointRepo.findById(cr.getPointId()).ifPresent(p -> pointMap.put(p.getId(), p));
        }
        return pointMap;
    }

    private static class StartCoordinateInfo {
        String startNodeId;
        String name;
        double lat;
        double lng;
    }

    private StartCoordinateInfo resolveStartLocation(OptimizationRequest req, Truck truck, Depot depot) {
        StartCoordinateInfo info = new StartCoordinateInfo();
        info.startNodeId = "DEPOT";
        info.name = depot.getName();
        info.lat = depot.getLatitude();
        info.lng = depot.getLongitude();

        if ("TRUCK_GPS".equalsIgnoreCase(req.startType) && truck.getLatitude() != null && truck.getLongitude() != null) {
            info.startNodeId = "TRUCK_" + truck.getId();
            info.name = truck.getName() + " (Latest GPS Position)";
            info.lat = truck.getLatitude();
            info.lng = truck.getLongitude();
        } else if ("CUSTOM".equalsIgnoreCase(req.startType) && req.customStartLat != null && req.customStartLng != null) {
            if (req.customStartLat < -90 || req.customStartLat > 90 || req.customStartLng < -180 || req.customStartLng > 180) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid custom coordinates: Latitude [-90,90], Longitude [-180,180].");
            }
            info.startNodeId = "CUSTOM_ORIGIN";
            info.name = (req.customStartName != null && !req.customStartName.isBlank()) ? req.customStartName : "Custom Start Location";
            info.lat = req.customStartLat;
            info.lng = req.customStartLng;
        }
        return info;
    }

    private static class PlannedLoadAllocation {
        List<CollectionRequest> assignedRequests = new ArrayList<>();
        double assignedTotalWasteKg = 0.0;
        Map<String, Integer> priorityCounts = new LinkedHashMap<>();
        List<UnassignedRequestDetail> unassignedDetails = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
    }

    private PlannedLoadAllocation allocateLoadUnderCapacity(Truck truck, List<CollectionRequest> requests, Map<String, CollectionPoint> pointMap) {
        PlannedLoadAllocation alloc = new PlannedLoadAllocation();
        alloc.priorityCounts.put("HIGH", 0);
        alloc.priorityCounts.put("MEDIUM", 0);
        alloc.priorityCounts.put("LOW", 0);

        List<CollectionRequest> sorted = new ArrayList<>(requests);
        Map<String, Integer> pWeight = Map.of("HIGH", 3, "MEDIUM", 2, "LOW", 1);
        sorted.sort((a, b) -> {
            int pDiff = pWeight.getOrDefault(b.getPriority(), 1) - pWeight.getOrDefault(a.getPriority(), 1);
            if (pDiff != 0) return pDiff;
            return Double.compare(b.getWasteKg(), a.getWasteKg());
        });

        double currentLoad = (truck.getCurrentRouteId() != null) ? truck.getAssignedLoadKg() : 0.0;
        double capacity = truck.getCapacityKg();

        for (CollectionRequest r : sorted) {
            CollectionPoint cp = pointMap.get(r.getPointId());
            if (cp == null || cp.getLatitude() == null || cp.getLongitude() == null) {
                alloc.unassignedDetails.add(new UnassignedRequestDetail(
                        r.getId(), r.getPointName(), r.getWasteKg(), r.getPriority(), "Missing geographical coordinates."
                ));
                alloc.warnings.add("Request #" + r.getId() + " skipped: missing coordinates for " + r.getPointName() + ".");
                continue;
            }

            if (currentLoad + r.getWasteKg() <= capacity) {
                alloc.assignedRequests.add(r);
                currentLoad += r.getWasteKg();
                alloc.assignedTotalWasteKg += r.getWasteKg();
                String pKey = (r.getPriority() != null) ? r.getPriority().toUpperCase() : "MEDIUM";
                alloc.priorityCounts.put(pKey, alloc.priorityCounts.getOrDefault(pKey, 0) + 1);
            } else {
                double overage = Math.round((currentLoad + r.getWasteKg() - capacity) * 10.0) / 10.0;
                alloc.unassignedDetails.add(new UnassignedRequestDetail(
                        r.getId(), r.getPointName(), r.getWasteKg(), r.getPriority(),
                        "Exceeds vehicle remaining capacity (" + Math.round((capacity - currentLoad) * 10.0) / 10.0 + " kg free, over by " + overage + " kg)."
                ));
            }
        }

        return alloc;
    }

    // -------------------------------------------------------------
    // 3. DIJKSTRA GRAPH BUILDER & PATH ENGINE
    // -------------------------------------------------------------
    public RoadNetwork buildMunicipalRoadNetwork(Depot depot,
                                                 Map<String, CollectionPoint> pointMap,
                                                 String startNodeId, double startLat, double startLng,
                                                 Map<String, double[]> nodeCoords) {
        RoadNetwork net = new RoadNetwork();

        // Core Depot Node
        String depotId = "DEPOT";
        net.addLocation(depotId, depot.getName());
        nodeCoords.put(depotId, new double[]{depot.getLatitude(), depot.getLongitude()});

        // Municipal Arterial Road Junctions
        String j1 = "J1"; // Gandhi Circle
        String j2 = "J2"; // Railway Junction
        String j3 = "J3"; // Market Cross
        net.addLocation(j1, "Gandhi Circle");
        nodeCoords.put(j1, new double[]{8.7930, 78.1360});
        net.addLocation(j2, "Railway Junction");
        nodeCoords.put(j2, new double[]{8.7880, 78.1310});
        net.addLocation(j3, "Market Cross");
        nodeCoords.put(j3, new double[]{8.7910, 78.1400});

        // Add base municipal road connections (positive edge weights in km)
        net.addRoad(depotId, j1, 2.0);
        net.addRoad(depotId, j2, 3.5);
        net.addRoad(j1, j3, 2.5);
        net.addRoad(j2, j3, 3.0);

        // Add Collection Points to Network
        for (CollectionPoint pt : pointMap.values()) {
            if (pt.getId() == null || pt.getLatitude() == null || pt.getLongitude() == null) continue;
            if (!net.hasLocation(pt.getId())) {
                net.addLocation(pt.getId(), pt.getName());
                nodeCoords.put(pt.getId(), new double[]{pt.getLatitude(), pt.getLongitude()});
            }
        }

        // Connect Collection Points to nearest Junctions & neighboring points
        for (CollectionPoint pt : pointMap.values()) {
            if (pt.getLatitude() == null || pt.getLongitude() == null) continue;
            String pid = pt.getId();

            String closestJunction = j1;
            double minJuncDist = Double.MAX_VALUE;
            for (String j : List.of(j1, j2, j3, depotId)) {
                double[] jc = nodeCoords.get(j);
                double d = haversineKm(pt.getLatitude(), pt.getLongitude(), jc[0], jc[1]) * 1.30;
                if (d < minJuncDist) {
                    minJuncDist = d;
                    closestJunction = j;
                }
            }
            double roadKm = Math.max(0.4, Math.round(minJuncDist * 100.0) / 100.0);
            if (net.getRoad(pid, closestJunction) == null) {
                net.addRoad(pid, closestJunction, roadKm);
            }

            // Neighboring mesh roads
            for (CollectionPoint other : pointMap.values()) {
                if (other.getId().equals(pid) || other.getLatitude() == null || other.getLongitude() == null) continue;
                double crossDist = haversineKm(pt.getLatitude(), pt.getLongitude(), other.getLatitude(), other.getLongitude()) * 1.25;
                if (crossDist <= 3.0 && net.getRoad(pid, other.getId()) == null) {
                    double crossKm = Math.max(0.4, Math.round(crossDist * 100.0) / 100.0);
                    net.addRoad(pid, other.getId(), crossKm);
                }
            }
        }

        // Municipal bypass road between J1 and J2
        if (net.getRoad(j1, j2) == null) {
            net.addRoad(j1, j2, 2.8);
        }

        // Apply active road restrictions from MySQL
        if (roadRestrictionRepo != null) {
            try {
                List<RoadRestriction> activeRestrictions = roadRestrictionRepo.findByActiveTrue();
                for (RoadRestriction rr : activeRestrictions) {
                    if (rr.getFromNode() != null && rr.getToNode() != null) {
                        if (net.getRoad(rr.getFromNode(), rr.getToNode()) != null && !net.isBlocked(rr.getFromNode(), rr.getToNode())) {
                            net.blockRoad(rr.getFromNode(), rr.getToNode());
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Add Origin Node if distinct from Depot
        if (!startNodeId.equals(depotId)) {
            if (!net.hasLocation(startNodeId)) {
                net.addLocation(startNodeId, "Route Origin");
                nodeCoords.put(startNodeId, new double[]{startLat, startLng});

                String closest = depotId;
                double minD = Double.MAX_VALUE;
                for (String j : List.of(j1, j2, j3, depotId)) {
                    double[] c = nodeCoords.get(j);
                    double d = haversineKm(startLat, startLng, c[0], c[1]) * 1.30;
                    if (d < minD) {
                        minD = d;
                        closest = j;
                    }
                }
                double dKm = Math.max(0.3, Math.round(minD * 100.0) / 100.0);
                net.addRoad(startNodeId, closest, dKm);
            }
        }

        return net;
    }

    // -------------------------------------------------------------
    // 4. MULTI-STOP DIJKSTRA OPTIMIZER WITH 2-OPT
    // -------------------------------------------------------------
    public List<CollectionRequest> orderStopsWithDijkstra(RoadNetwork net,
                                                          String startNodeId,
                                                          String depotNodeId,
                                                          List<CollectionRequest> requests,
                                                          Map<String, CollectionPoint> pointMap) {
        if (requests.size() <= 1) return requests;

        List<CollectionRequest> remaining = new ArrayList<>(requests);
        List<CollectionRequest> ordered = new ArrayList<>();
        String currentNodeId = startNodeId;

        // Nearest-Neighbour over Dijkstra Shortest-Path Distances with Priority Tier Bias
        while (!remaining.isEmpty()) {
            int bestIdx = 0;
            double bestScore = Double.MAX_VALUE;

            for (int i = 0; i < remaining.size(); i++) {
                CollectionRequest cand = remaining.get(i);
                PathResult path = routePlanner.shortestPath(net, currentNodeId, cand.getPointId());
                double dist = path.isReachable() ? path.getDistanceKm() : 999.0;

                // Priority discount factor: HIGH priority gets preference, reducing effective distance
                double priorityFactor = "HIGH".equalsIgnoreCase(cand.getPriority()) ? 0.65 :
                                        ("MEDIUM".equalsIgnoreCase(cand.getPriority()) ? 0.90 : 1.10);
                double score = dist * priorityFactor;

                if (score < bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }

            CollectionRequest chosen = remaining.remove(bestIdx);
            ordered.add(chosen);
            currentNodeId = chosen.getPointId();
        }

        // 2-Opt Local Search Refinement using Dijkstra Tour Length
        boolean improved = true;
        while (improved && ordered.size() > 2) {
            improved = false;
            double currentTourDist = calculateDijkstraTourDistance(net, startNodeId, depotNodeId, ordered);

            for (int i = 0; i < ordered.size() - 1; i++) {
                for (int j = i + 1; j < ordered.size(); j++) {
                    List<CollectionRequest> swapped = new ArrayList<>();
                    swapped.addAll(ordered.subList(0, i));
                    List<CollectionRequest> reversedSub = new ArrayList<>(ordered.subList(i, j + 1));
                    Collections.reverse(reversedSub);
                    swapped.addAll(reversedSub);
                    swapped.addAll(ordered.subList(j + 1, ordered.size()));

                    double newTourDist = calculateDijkstraTourDistance(net, startNodeId, depotNodeId, swapped);
                    if (newTourDist < currentTourDist - 1e-4) {
                        ordered = swapped;
                        currentTourDist = newTourDist;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
        }

        return ordered;
    }

    private double calculateDijkstraTourDistance(RoadNetwork net, String startNodeId, String depotNodeId, List<CollectionRequest> stops) {
        double dist = 0.0;
        String cur = startNodeId;
        for (CollectionRequest r : stops) {
            PathResult p = routePlanner.shortestPath(net, cur, r.getPointId());
            dist += p.isReachable() ? p.getDistanceKm() : 50.0;
            cur = r.getPointId();
        }
        PathResult toDepot = routePlanner.shortestPath(net, cur, depotNodeId);
        dist += toDepot.isReachable() ? toDepot.getDistanceKm() : 50.0;
        return dist;
    }

    // -------------------------------------------------------------
    // 5. ROAD ROUTE & GEOMETRY CALCULATION
    // -------------------------------------------------------------
    public static class RouteCalculation {
        public double distanceKm;
        public double durationMin;
        public double drivingTimeMin;
        public double collectionTimeMin;
        public String method;
        public String geometryJson;
        public List<SegmentDetail> segments = new ArrayList<>();
    }

    public RouteCalculation calculateDijkstraRoadRoute(RoadNetwork net, Depot depot,
                                                       String startNodeId, double startLat, double startLng,
                                                       List<CollectionRequest> stops,
                                                       Map<String, CollectionPoint> pointMap,
                                                       Map<String, double[]> nodeCoords) {
        boolean hasActiveRestrictions = roadRestrictionRepo != null && !roadRestrictionRepo.findByActiveTrue().isEmpty();

        // 3. Authoritative Dijkstra Road Network Shortest Path
        RouteCalculation dijkstraCalc = new RouteCalculation();
        double totalDijkstraDist = 0.0;
        double totalDrivingTimeMin = 0.0;
        double totalCollectionTimeMin = 0.0;
        List<List<Double>> pathCoords = new ArrayList<>();
        List<SegmentDetail> segments = new ArrayList<>();

        String cur = startNodeId;
        String curName = (nodeCoords.containsKey(cur) && cur.equals("DEPOT")) ? depot.getName() : "Route Start";
        double curLat = startLat;
        double curLng = startLng;
        int segIdx = 1;

        for (CollectionRequest r : stops) {
            CollectionPoint cp = pointMap.get(r.getPointId());
            double targetLat = (cp != null && cp.getLatitude() != null) ? cp.getLatitude() : (nodeCoords.containsKey(r.getPointId()) ? nodeCoords.get(r.getPointId())[0] : curLat);
            double targetLng = (cp != null && cp.getLongitude() != null) ? cp.getLongitude() : (nodeCoords.containsKey(r.getPointId()) ? nodeCoords.get(r.getPointId())[1] : curLng);
            String targetName = r.getPointName();

            PathResult leg = routePlanner.shortestPath(net, cur, r.getPointId());
            double legDist = leg.isReachable() ? leg.getDistanceKm() : haversineKm(curLat, curLng, targetLat, targetLng) * 1.30;
            legDist = Math.max(0.2, Math.round(legDist * 10.0) / 10.0);
            double legDriveTime = Math.max(0.5, Math.round((legDist / 28.0) * 60.0 * 10.0) / 10.0);
            double legCollectTime = 5.0; // 5 mins service time per collection stop
            double legTotalTime = Math.round((legDriveTime + legCollectTime) * 10.0) / 10.0;

            totalDijkstraDist += legDist;
            totalDrivingTimeMin += legDriveTime;
            totalCollectionTimeMin += legCollectTime;

            segments.add(new SegmentDetail(
                    segIdx++,
                    curName, curLat, curLng,
                    targetName, targetLat, targetLng,
                    legDist, legDriveTime, legCollectTime, legTotalTime, "PENDING"
            ));

            if (leg.isReachable() && leg.getNodes() != null && !leg.getNodes().isEmpty()) {
                for (String node : leg.getNodes()) {
                    double[] c = nodeCoords.get(node);
                    if (c != null) {
                        if (pathCoords.isEmpty() || !isSameCoord(pathCoords.get(pathCoords.size() - 1), c[1], c[0])) {
                            pathCoords.add(List.of(c[1], c[0]));
                        }
                    }
                }
            } else {
                pathCoords.add(List.of(targetLng, targetLat));
            }

            cur = r.getPointId();
            curName = targetName;
            curLat = targetLat;
            curLng = targetLng;
        }

        // Final Leg back to Depot
        PathResult returnLeg = routePlanner.shortestPath(net, cur, "DEPOT");
        double returnDist = returnLeg.isReachable() ? returnLeg.getDistanceKm() : haversineKm(curLat, curLng, depot.getLatitude(), depot.getLongitude()) * 1.30;
        returnDist = Math.max(0.2, Math.round(returnDist * 10.0) / 10.0);
        double returnDriveTime = Math.max(0.5, Math.round((returnDist / 28.0) * 60.0 * 10.0) / 10.0);
        double returnCollectTime = 0.0; // Depot unloading
        double returnTotalTime = returnDriveTime;

        totalDijkstraDist += returnDist;
        totalDrivingTimeMin += returnDriveTime;

        segments.add(new SegmentDetail(
                segIdx++,
                curName, curLat, curLng,
                depot.getName() + " (Final Unloading)", depot.getLatitude(), depot.getLongitude(),
                returnDist, returnDriveTime, returnCollectTime, returnTotalTime, "PENDING"
        ));

        if (returnLeg.isReachable() && returnLeg.getNodes() != null && !returnLeg.getNodes().isEmpty()) {
            for (String node : returnLeg.getNodes()) {
                double[] c = nodeCoords.get(node);
                if (c != null) {
                    if (pathCoords.isEmpty() || !isSameCoord(pathCoords.get(pathCoords.size() - 1), c[1], c[0])) {
                        pathCoords.add(List.of(c[1], c[0]));
                    }
                }
            }
        } else {
            pathCoords.add(List.of(depot.getLongitude(), depot.getLatitude()));
        }

        dijkstraCalc.distanceKm = Math.max(1.0, Math.round(totalDijkstraDist * 10.0) / 10.0);
        dijkstraCalc.drivingTimeMin = Math.round(totalDrivingTimeMin * 10.0) / 10.0;
        dijkstraCalc.collectionTimeMin = Math.round(totalCollectionTimeMin * 10.0) / 10.0;
        dijkstraCalc.durationMin = Math.round((totalDrivingTimeMin + totalCollectionTimeMin) * 10.0) / 10.0;
        dijkstraCalc.method = hasActiveRestrictions ? "DIJKSTRA_ROAD_NETWORK (RESTRICTION_AVOIDED)" : "DIJKSTRA_ROAD_NETWORK";
        dijkstraCalc.segments = segments;

        try {
            dijkstraCalc.geometryJson = objectMapper.writeValueAsString(pathCoords);
        } catch (Exception e) {
            dijkstraCalc.geometryJson = "[]";
        }

        return dijkstraCalc;
    }

    public String[] findNearestRoadEdge(double lat, double lng) {
        Depot depot = getOrCreateDepot();
        Map<String, CollectionPoint> pointMap = new HashMap<>();
        for (CollectionPoint cp : pointRepo.findAll()) {
            if (cp.getId() != null) pointMap.put(cp.getId(), cp);
        }
        Map<String, double[]> nodeCoords = new HashMap<>();
        RoadNetwork net = buildMunicipalRoadNetwork(depot, pointMap, "DEPOT", depot.getLatitude(), depot.getLongitude(), nodeCoords);

        String bestFrom = "J1";
        String bestTo = "J3";
        double bestDist = Double.MAX_VALUE;

        for (String u : net.getLocationIds()) {
            for (RoadNetwork.Road r : net.getNeighbours(u)) {
                String v = r.getDestination();
                if (u.compareTo(v) < 0) { // check each undirected edge once
                    double[] c1 = nodeCoords.get(u);
                    double[] c2 = nodeCoords.get(v);
                    if (c1 != null && c2 != null) {
                        double d = distanceToSegmentKm(lat, lng, c1[0], c1[1], c2[0], c2[1]);
                        if (d < bestDist) {
                            bestDist = d;
                            bestFrom = u;
                            bestTo = v;
                        }
                    }
                }
            }
        }
        return new String[]{bestFrom, bestTo};
    }

    private double distanceToSegmentKm(double pLat, double pLng, double aLat, double aLng, double bLat, double bLng) {
        double dAB2 = (bLat - aLat) * (bLat - aLat) + (bLng - aLng) * (bLng - aLng);
        if (dAB2 < 1e-12) {
            return haversineKm(pLat, pLng, aLat, aLng);
        }
        double t = ((pLat - aLat) * (bLat - aLat) + (pLng - aLng) * (bLng - aLng)) / dAB2;
        t = Math.max(0.0, Math.min(1.0, t));
        double projLat = aLat + t * (bLat - aLat);
        double projLng = aLng + t * (bLng - aLng);
        return haversineKm(pLat, pLng, projLat, projLng);
    }

    @Transactional
    public RoutePlan recalculateRouteForRoadIssue(Long routePlanId, RoadIssue issue, String adminUsername) {
        Optional<RoutePlan> optPlan = routePlanRepo.findById(routePlanId);
        if (optPlan.isEmpty()) return null;
        RoutePlan plan = optPlan.get();

        if ("COMPLETED".equalsIgnoreCase(plan.getStatus()) || "CANCELLED".equalsIgnoreCase(plan.getStatus())) {
            return plan;
        }

        // 1. Snapshot previous route version into revision history
        if (routeRevisionRepo != null) {
            RouteRevision rev = new RouteRevision(
                    plan.getId(),
                    plan.getRevisionNumber(),
                    "Revision prior to recalculation for " + issue.getIssueType() + " (#" + issue.getId() + "): " + issue.getDescription(),
                    issue.getId(),
                    plan.getTotalDistanceKm(),
                    plan.getEstimatedDurationMin(),
                    plan.getRouteGeometryJson(),
                    plan.getRoutingMethod(),
                    adminUsername
            );
            routeRevisionRepo.save(rev);
        }

        List<RouteStop> allStops = routeStopRepo.findByRoutePlanIdOrderByStopOrderAsc(plan.getId());
        List<RouteStop> completedStops = allStops.stream().filter(s -> "COMPLETED".equals(s.getStatus())).toList();
        List<RouteStop> remainingStops = allStops.stream().filter(s -> !"COMPLETED".equals(s.getStatus())).toList();

        if (remainingStops.isEmpty()) {
            return plan;
        }

        Depot depot = getOrCreateDepot();

        // 2. Determine starting location for remaining route
        double startLat;
        double startLng;
        String startNodeId;
        String startLocName;

        Optional<Truck> truckOpt = truckRepo.findById(plan.getTruckId());
        if (truckOpt.isPresent() && truckOpt.get().getLatitude() != null && truckOpt.get().getLongitude() != null) {
            Truck truck = truckOpt.get();
            startLat = truck.getLatitude();
            startLng = truck.getLongitude();
            startNodeId = "TRUCK_GPS_" + truck.getId();
            startLocName = truck.getName() + " (Live GPS Position)";
        } else if (!completedStops.isEmpty()) {
            RouteStop lastDone = completedStops.get(completedStops.size() - 1);
            startLat = lastDone.getLatitude();
            startLng = lastDone.getLongitude();
            startNodeId = lastDone.getPointId();
            startLocName = lastDone.getPointName() + " (Last Completed Stop)";
        } else {
            startLat = plan.getPlannedStartLat();
            startLng = plan.getPlannedStartLng();
            startNodeId = "ORIGIN";
            startLocName = plan.getStartLocationName();
        }

        // 3. Load Collection Points map for remaining stops
        Map<String, CollectionPoint> pointMap = new HashMap<>();
        for (RouteStop s : remainingStops) {
            pointRepo.findById(s.getPointId()).ifPresent(cp -> pointMap.put(cp.getId(), cp));
        }

        // 4. Build Municipal Road Network with the blocked restriction active
        Map<String, double[]> nodeCoords = new HashMap<>();
        RoadNetwork net = buildMunicipalRoadNetwork(depot, pointMap, startNodeId, startLat, startLng, nodeCoords);

        // Ensure issue edge is strictly blocked
        if (issue.getFromNode() != null && issue.getToNode() != null) {
            if (net.getRoad(issue.getFromNode(), issue.getToNode()) != null && !net.isBlocked(issue.getFromNode(), issue.getToNode())) {
                try {
                    net.blockRoad(issue.getFromNode(), issue.getToNode());
                } catch (Exception ignored) {}
            }
        }

        // 5. Wrap remaining stops as CollectionRequests for Dijkstra ordering
        List<CollectionRequest> remainingRequests = new ArrayList<>();
        Map<Long, RouteStop> reqToStopMap = new HashMap<>();
        for (RouteStop s : remainingStops) {
            CollectionRequest mockReq = new CollectionRequest(s.getPointId(), s.getPointName(), s.getPriority(), s.getExpectedWasteKg(), "GENERAL", plan.getTruckName());
            mockReq.setId(s.getRequestId() != null ? s.getRequestId() : s.getId());
            remainingRequests.add(mockReq);
            reqToStopMap.put(mockReq.getId(), s);
        }

        List<CollectionRequest> orderedRemaining = orderStopsWithDijkstra(net, startNodeId, "DEPOT", remainingRequests, pointMap);

        // 6. Calculate new road path & geometry avoiding blocked edges
        RouteCalculation calc = calculateDijkstraRoadRoute(net, depot, startNodeId, startLat, startLng, orderedRemaining, pointMap, nodeCoords);

        // 7. Re-sequence stop orders for remaining stops
        int newOrder = completedStops.size() + 1;
        for (CollectionRequest r : orderedRemaining) {
            RouteStop s = reqToStopMap.get(r.getId());
            if (s != null) {
                s.setStopOrder(newOrder++);
                routeStopRepo.save(s);
            }
        }

        // 8. Update RoutePlan in MySQL
        plan.setRouteGeometryJson(calc.geometryJson);
        plan.setTotalDistanceKm(calc.distanceKm);
        plan.setEstimatedDurationMin(calc.durationMin);
        plan.setRoutingMethod("DIJKSTRA_ROAD_NETWORK (RESTRICTION_AVOIDED)");
        plan.setRevisionNumber(plan.getRevisionNumber() + 1);
        plan.setLastRecalculatedAt(LocalDateTime.now());
        plan.setTriggeredByRoadIssueId(issue.getId());
        plan.setRecalculationReason("Rerouted to avoid " + issue.getIssueType() + " at " + (issue.getLocationName() != null ? issue.getLocationName() : "road " + issue.getFromNode() + "-" + issue.getToNode()) + " (Issue #" + issue.getId() + ")");

        try {
            plan.setSegmentDetailsJson(objectMapper.writeValueAsString(calc.segments));
        } catch (Exception ignored) {}

        RoutePlan saved = routePlanRepo.save(plan);
        auditService.record("ROUTE_RECALCULATED", "Route #" + plan.getId() + " recalculated to revision " + plan.getRevisionNumber() + " (" + calc.distanceKm + " km, " + calc.durationMin + " min) avoiding " + issue.getFromNode() + "-" + issue.getToNode(), adminUsername, plan.getId());
        return saved;
    }

    private boolean isSameCoord(List<Double> coord, double lng, double lat) {
        return Math.abs(coord.get(0) - lng) < 1e-6 && Math.abs(coord.get(1) - lat) < 1e-6;
    }

    private RouteCalculation callOpenRouteService(List<double[]> waypoints) {
        try {
            List<List<Double>> orsCoords = new ArrayList<>();
            for (double[] wp : waypoints) {
                orsCoords.add(List.of(wp[1], wp[0])); // [lng, lat]
            }
            Map<String, Object> reqBody = Map.of("coordinates", orsCoords);

            String resp = restClient.post()
                    .uri("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
                    .header("Authorization", orsApiKey.trim())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(reqBody))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(resp);
            JsonNode feat = root.path("features").get(0);
            if (feat != null) {
                JsonNode props = feat.path("properties").path("summary");
                double distKm = props.path("distance").asDouble(0.0) / 1000.0;
                double durMin = props.path("duration").asDouble(0.0) / 60.0;
                JsonNode geomCoords = feat.path("geometry").path("coordinates");

                RouteCalculation calc = new RouteCalculation();
                calc.distanceKm = distKm;
                calc.durationMin = durMin;
                calc.method = "ORS_ROAD_API";
                calc.geometryJson = geomCoords.toString();
                return calc;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private RouteCalculation callOsrmDriving(List<double[]> waypoints) {
        try {
            StringBuilder sb = new StringBuilder("https://router.project-osrm.org/route/v1/driving/");
            for (int i = 0; i < waypoints.size(); i++) {
                if (i > 0) sb.append(";");
                sb.append(waypoints.get(i)[1]).append(",").append(waypoints.get(i)[0]);
            }
            sb.append("?overview=full&geometries=geojson");

            String resp = restClient.get()
                    .uri(sb.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(resp);
            JsonNode route = root.path("routes").get(0);
            if (route != null) {
                double distKm = route.path("distance").asDouble(0.0) / 1000.0;
                double durMin = route.path("duration").asDouble(0.0) / 60.0;
                JsonNode coords = route.path("geometry").path("coordinates");

                RouteCalculation calc = new RouteCalculation();
                calc.distanceKm = distKm;
                calc.durationMin = durMin;
                calc.method = "ROAD_OSRM_PROXY";
                calc.geometryJson = coords.toString();
                return calc;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}