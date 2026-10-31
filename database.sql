-- Database creation
CREATE DATABASE IF NOT EXISTS marudham_waste;
USE marudham_waste;

-- Core Tables created and auto-updated by Hibernate (spring.jpa.hibernate.ddl-auto=update)
-- 1. app_users: system credentials, roles (ADMIN, DRIVER, USER)
-- 2. depots: municipal unloading facilities
-- 3. trucks: fleet vehicles, capacities, GPS coordinates, assigned drivers
-- 4. collection_points: waste collection bins and zones
-- 5. collection_requests: citizen/dispatcher pickup requests
-- 6. route_plans: authoritative multi-stop route plans with Dijkstra road geometry
-- 7. route_stops: ordered sequence of collection stops per route plan
-- 8. gps_history: real-time GPS location audit logs
-- 9. audit_events: security and operational audit trail

-- NEW TABLES FOR INTELLIGENT ROUTE MAPPING & BLOCKAGE MANAGEMENT:

-- 10. road_issues: driver-reported road blocks, damage, and maintenance
CREATE TABLE IF NOT EXISTS road_issues (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  issue_type VARCHAR(50) NOT NULL,
  description VARCHAR(500) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  location_name VARCHAR(150),
  reporting_driver_username VARCHAR(50) NOT NULL,
  truck_id VARCHAR(20),
  status VARCHAR(30) NOT NULL DEFAULT 'REPORTED',
  photo_url LONGTEXT,
  from_node VARCHAR(50),
  to_node VARCHAR(50),
  affected_route_id BIGINT,
  admin_notes VARCHAR(500),
  created_at DATETIME(6) NOT NULL,
  verified_at DATETIME(6),
  resolved_at DATETIME(6),
  verified_by VARCHAR(50)
);

-- 11. road_restrictions: active road network graph restrictions
CREATE TABLE IF NOT EXISTS road_restrictions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  road_issue_id BIGINT,
  from_node VARCHAR(50) NOT NULL,
  to_node VARCHAR(50) NOT NULL,
  reason VARCHAR(255),
  active BIT(1) NOT NULL DEFAULT 1,
  activated_at DATETIME(6) NOT NULL,
  activated_by VARCHAR(50),
  deactivated_at DATETIME(6)
);

-- 12. route_revision_history: immutable archive of rerouted plans
CREATE TABLE IF NOT EXISTS route_revision_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_plan_id BIGINT NOT NULL,
  revision_number INT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  road_issue_id BIGINT,
  total_distance_km DOUBLE NOT NULL,
  estimated_duration_min DOUBLE NOT NULL,
  route_geometry_json LONGTEXT,
  routing_method VARCHAR(50),
  revised_at DATETIME(6) NOT NULL,
  revised_by VARCHAR(50)
);
