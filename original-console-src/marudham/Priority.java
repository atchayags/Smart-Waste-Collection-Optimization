package marudham;

/**
 * Request priority levels. A higher weight is served first.
 */
public enum Priority {
    HIGH(3), MEDIUM(2), LOW(1);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    /** Parses H/M/L, HIGH/MEDIUM/LOW (any case) or 1/2/3 (1=High). Returns null if invalid. */
    public static Priority parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toUpperCase();
        switch (t) {
            case "H": case "HIGH": case "1": return HIGH;
            case "M": case "MEDIUM": case "2": return MEDIUM;
            case "L": case "LOW": case "3": return LOW;
            default: return null;
        }
    }
}
