package com.marudham.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import com.marudham.web.service.PasswordService;

@SpringBootApplication
public class MarudhamWasteWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarudhamWasteWebApplication.class, args);
    }

    private static final double[][] DEFAULT_COORDS = {
        {8.7960, 78.1330}, {8.7900, 78.1450}, {8.7830, 78.1290}, {8.7780, 78.1400},
        {8.7850, 78.1520}, {8.7930, 78.1250}, {8.7720, 78.1350}, {8.8020, 78.1480}, {8.7890, 78.1600}
    };

    @Bean
    CommandLineRunner seed(CollectionPointRepository points, TruckRepository trucks, AppUserRepository users,
                           DepotRepository depots, PasswordService passwords,
                           @Value("${app.admin.password:admin123}") String adminPassword) {
        return args -> {
            // Seed persistent Depot if not present
            if (depots.count() == 0) {
                depots.save(new Depot("Marudham Central Depot", "Central Municipal Yard, Main Road, Marudham", 8.7960, 78.1330));
            }

            // Seed Collection Points if empty or missing key locations
            if (points.count() == 0) {
                points.save(new CollectionPoint("CP01", "Market Street", "Central", 180.0, 8.7960, 78.1330));
                points.save(new CollectionPoint("CP02", "Bus Stand", "North", 320.0, 8.7900, 78.1450));
                points.save(new CollectionPoint("CP03", "College Road", "East", 95.0, 8.7830, 78.1290));
                points.save(new CollectionPoint("CP04", "Lake View", "South", 410.0, 8.7780, 78.1400));
                points.save(new CollectionPoint("CP05", "Town Hall", "Central", 240.0, 8.7990, 78.1380));
                points.save(new CollectionPoint("CP06", "Industrial Estate", "West", 510.0, 8.8050, 78.1250));
            }
            if (!points.existsById("CP05")) {
                points.save(new CollectionPoint("CP05", "Town Hall", "Central", 240.0, 8.7990, 78.1380));
            }
            if (!points.existsById("CP06")) {
                points.save(new CollectionPoint("CP06", "Industrial Estate", "West", 510.0, 8.8050, 78.1250));
            }

            // Ensure coordinates and active status exist on all points
            int i = 0;
            for (CollectionPoint p : points.findAll()) {
                boolean changed = false;
                if (!p.isActive()) {
                    p.setActive(true);
                    changed = true;
                }
                if (p.getLatitude() == null || p.getLongitude() == null) {
                    double[] c = DEFAULT_COORDS[i++ % DEFAULT_COORDS.length];
                    p.setLatitude(c[0]);
                    p.setLongitude(c[1]);
                    changed = true;
                }
                if (changed) {
                    points.save(p);
                }
            }

            // Seed Trucks if empty
            if (trucks.count() == 0) {
                Truck t1 = new Truck("T1", "Truck 1", 800.0);
                t1.setLatitude(8.7960);
                t1.setLongitude(78.1330);
                t1.setAssignedDriverUsername("driver1");
                trucks.save(t1);

                Truck t2 = new Truck("T2", "Truck 2", 600.0);
                t2.setLatitude(8.7960);
                t2.setLongitude(78.1330);
                trucks.save(t2);
            }

            // Seed Default Roles/Users
            if (!users.existsByUsername("admin")) {
                users.save(new AppUser("admin", passwords.hash(adminPassword), "ADMIN", "Administrator"));
            }
            if (!users.existsByUsername("driver1")) {
                users.save(new AppUser("driver1", passwords.hash("driver123"), "DRIVER", "Murugan (Driver)"));
            }
            if (!users.existsByUsername("citizen1")) {
                users.save(new AppUser("citizen1", passwords.hash("citizen123"), "USER", "Kavitha (Citizen)"));
            }
        };
    }
}
