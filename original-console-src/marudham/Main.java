package marudham;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Smart City Waste Collection Optimization - Marudham Municipality.
 * Run with no arguments for the interactive menu, or with "demo" for a scripted walkthrough.
 */
public class Main {
    private static Scanner in;

    public static void main(String[] args) {
        CollectionService service = SampleData.createService();
        if (args.length > 0 && args[0].equalsIgnoreCase("demo")) {
            runDemo(service);
            return;
        }
        in = new Scanner(System.in);
        System.out.println("=== Smart City Waste Collection Optimization : Marudham Municipality ===");
        try {
            menuLoop(service);
        } catch (NoSuchElementException e) {
            System.out.println("\nInput ended. Goodbye.");
        }
    }

    // ------------------------------------------------------------------ menu

    private static void printMenu() {
        System.out.println();
        System.out.println("------------------------------ MENU ------------------------------");
        System.out.println(" 1  View road network              12  Block a road");
        System.out.println(" 2  View collection points         13  Reopen a road");
        System.out.println(" 3  View vehicles                  14  Report vehicle breakdown");
        System.out.println(" 4  Load sample requests (wave 1)  15  Repair a vehicle");
        System.out.println(" 5  Load sample requests (wave 2)  16  Return a vehicle to depot & unload");
        System.out.println(" 6  Submit a new request           17  End shift (recall all vehicles)");
        System.out.println(" 7  View pending requests          18  Shortest path between two locations");
        System.out.println(" 8  Assign next request            19  Compare priority vs nearest-neighbour order");
        System.out.println(" 9  Advance a vehicle (N hops)     20  View history");
        System.out.println("10  Confirm collection             21  View summary report");
        System.out.println("11  Process everything (auto)      22  Deferred requests (view / retry)");
        System.out.println("23  Update a point's waste level    0  Exit");
        System.out.println("------------------------------------------------------------------");
    }

    private static void menuLoop(CollectionService s) {
        while (true) {
            printMenu();
            int choice = askInt("Choose an option: ", 0, 23);
            if (choice == 0) {
                System.out.println("Goodbye.");
                return;
            }
            try {
                handle(s, choice);
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }

    private static void handle(CollectionService s, int choice) {
        switch (choice) {
            case 1: System.out.print(s.getNetwork().describe()); break;
            case 2: System.out.print(s.pointsReport()); break;
            case 3: System.out.print(s.vehiclesReport()); break;
            case 4: printAll(SampleData.loadWave1(s)); break;
            case 5: printAll(SampleData.loadWave2(s)); break;
            case 6: {
                String point = askPoint(s, "Collection point id (e.g. CP3): ");
                Priority p = askPriority();
                CollectionRequest r = s.submitRequest(point, p);
                System.out.println("  Accepted: " + r);
                break;
            }
            case 7: System.out.print(s.pendingReport()); break;
            case 8: System.out.println("  " + s.assignNext().getMessage()); break;
            case 9: {
                String v = askVehicle(s, "Vehicle id: ");
                int hops = askInt("Hops to move (1-50): ", 1, 50);
                System.out.println("  " + s.advance(v, hops));
                break;
            }
            case 10: {
                String v = askVehicle(s, "Vehicle id: ");
                String txt = ask("Actual kg collected (Enter = everything at the point): ");
                Double kg = null;
                if (!txt.isEmpty()) {
                    try {
                        kg = Double.parseDouble(txt);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("'" + txt + "' is not a number.");
                    }
                }
                System.out.println("  " + s.confirmCollection(v, kg));
                break;
            }
            case 11: printAll(s.processAll()); System.out.println("  Done. Use option 17 to recall vehicles to the depot."); break;
            case 12: {
                String a = askLocation(s, "First end of the road: ");
                String b = askLocation(s, "Other end of the road: ");
                printAll(s.blockRoad(a, b));
                break;
            }
            case 13: {
                String a = askLocation(s, "First end of the road: ");
                String b = askLocation(s, "Other end of the road: ");
                System.out.println("  " + s.unblockRoad(a, b));
                break;
            }
            case 14: printAll(s.reportBreakdown(askVehicle(s, "Vehicle id: "))); break;
            case 15: System.out.println("  " + s.repairVehicle(askVehicle(s, "Vehicle id: "))); break;
            case 16: System.out.println("  " + s.returnToDepot(askVehicle(s, "Vehicle id: "))); break;
            case 17: printAll(s.endShift()); break;
            case 18: {
                String a = askLocation(s, "From: ");
                String b = askLocation(s, "To: ");
                PathResult p = s.getPlanner().shortestPath(s.getNetwork(), a, b);
                System.out.println("  " + (p.isReachable() ? p.toString() : "No open route between " + a + " and " + b + "."));
                break;
            }
            case 19: System.out.println(s.compareOrderings(askVehicle(s, "Vehicle id (start position): "))); break;
            case 20: System.out.print(s.historyReport()); break;
            case 21: System.out.print(s.summaryReport()); break;
            case 22: {
                System.out.print(s.deferredReport());
                if (!s.getDeferred().isEmpty()
                        && ask("Re-queue all deferred requests? (y/n): ").equalsIgnoreCase("y")) {
                    System.out.println("  " + s.retryDeferred() + " request(s) re-queued.");
                }
                break;
            }
            case 23: {
                String p = askPoint(s, "Collection point id: ");
                double kg = askDouble("New waste level in kg (0-5000): ", 0, CollectionPoint.MAX_WASTE_KG);
                s.setWasteLevel(p, kg);
                System.out.println("  Waste level updated.");
                break;
            }
            default: System.out.println("  Unknown option.");
        }
    }

    // ----------------------------------------------------------- input helpers

    private static void printAll(List<String> lines) {
        for (String l : lines) {
            System.out.println("  " + l);
        }
    }

    private static String ask(String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private static int askInt(String prompt, int min, int max) {
        while (true) {
            String t = ask(prompt);
            try {
                int v = Integer.parseInt(t);
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the message
            }
            System.out.println("  Please enter a whole number from " + min + " to " + max + ".");
        }
    }

    private static double askDouble(String prompt, double min, double max) {
        while (true) {
            String t = ask(prompt);
            try {
                double v = Double.parseDouble(t);
                if (!Double.isNaN(v) && v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the message
            }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    private static String askLocation(CollectionService s, String prompt) {
        while (true) {
            String t = ask(prompt).toUpperCase();
            if (s.getNetwork().hasLocation(t)) {
                return t;
            }
            System.out.println("  Unknown location. Valid ids: " + s.getNetwork().getLocationIds());
        }
    }

    private static String askPoint(CollectionService s, String prompt) {
        while (true) {
            String t = ask(prompt).toUpperCase();
            if (s.hasPoint(t)) {
                return t;
            }
            System.out.println("  Not a collection point. Use CP1 to CP9.");
        }
    }

    private static String askVehicle(CollectionService s, String prompt) {
        while (true) {
            String t = ask(prompt).toUpperCase();
            CollectionVehicle v = s.findVehicle(t);
            if (v != null) {
                return v.getVehicleId();
            }
            System.out.println("  Unknown vehicle. Use ST-1, LT-1 or LT-2.");
        }
    }

    private static Priority askPriority() {
        while (true) {
            Priority p = Priority.parse(ask("Priority (H/M/L or High/Medium/Low): "));
            if (p != null) {
                return p;
            }
            System.out.println("  Invalid priority.");
        }
    }

    // -------------------------------------------------------------- demo mode

    private static void banner(String text) {
        System.out.println();
        System.out.println("### " + text);
    }

    private static void show(String text) {
        System.out.println("  " + text);
    }

    private static void runDemo(CollectionService s) {
        System.out.println("=== DEMO: Smart City Waste Collection Optimization - Marudham ===");
        banner("1. Road network, points and fleet");
        System.out.print(s.getNetwork().describe());
        System.out.print(s.pointsReport());
        System.out.print(s.vehiclesReport());

        banner("2. Wave 1 requests (9) and the priority queue order");
        for (String l : SampleData.loadWave1(s)) {
            show(l);
        }
        System.out.print(s.pendingReport());

        banner("3. Assign the first three requests (the three HIGH ones go first)");
        for (int i = 0; i < 3; i++) {
            show(s.assignNext().getMessage());
        }
        show(s.assignNext().getMessage());

        banner("4. ST-1 drives one hop, then road J1-J3 is blocked: dynamic re-planning");
        show(s.advance("ST-1", 1));
        for (String l : s.blockRoad("J1", "J3")) {
            show(l);
        }

        banner("5. LT-1 drives one hop, then breaks down: reassignment");
        show(s.advance("LT-1", 1));
        for (String l : s.reportBreakdown("LT-1")) {
            show(l);
        }

        banner("6. Automatic processing of all remaining wave-1 requests");
        for (String l : s.processAll()) {
            show(l);
        }
        for (String l : s.endShift()) {
            show(l);
        }
        System.out.print(s.summaryReport());

        banner("7. Repair LT-1, reopen the road, load wave 2 (8 more requests, 17 in total)");
        show(s.repairVehicle("LT-1"));
        show(s.unblockRoad("J1", "J3"));
        for (String l : SampleData.loadWave2(s)) {
            show(l);
        }
        System.out.print(s.pendingReport());
        for (String l : s.processAll()) {
            show(l);
        }
        for (String l : s.endShift()) {
            show(l);
        }

        banner("8. Final reports");
        System.out.print(s.historyReport());
        System.out.print(s.summaryReport());
    }
}
