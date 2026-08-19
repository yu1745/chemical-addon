package com.yu1745.chemengine;

/** A chemical ion identity, e.g. Na+1, Ca+2, SO4-2. Pure string/charge; no Minecraft types. */
public final class Ion {

    private final String symbol;
    private final int charge;

    public Ion(String symbol, int charge) {
        this.symbol = symbol;
        this.charge = charge;
    }

    public String symbol() { return symbol; }
    public int charge() { return charge; }

    /** Canonical map key: "Ca+2", "SO4-2", "Na+1". */
    public String id() {
        return charge >= 0 ? symbol + "+" + charge : symbol + charge;
    }

    /**
     * Parse the charge out of a canonical ion id ("H+1" -> +1, "SO4-2" -> -2).
     * The sign sits just before the trailing magnitude; no sign => 0.
     */
    public static int chargeOf(String ionId) {
        int idx = -1;
        char sign = 0;
        for (int i = ionId.length() - 1; i >= 0; i--) {
            char c = ionId.charAt(i);
            if (c == '+' || c == '-') { idx = i; sign = c; break; }
        }
        if (idx < 0) return 0;
        String mag = ionId.substring(idx + 1);
        int m = mag.isEmpty() ? 1 : Integer.parseInt(mag);
        return sign == '+' ? m : -m;
    }

    @Override public String toString() { return id(); }
}
