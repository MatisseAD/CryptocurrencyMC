package fr.jachou.cryptocurrency.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChartFormatter {

    public static class Stats {
        public final double min;
        public final double max;
        public final double last;
        public final double first;
        public final double deltaPct;
        public final int points;
        public Stats(double min, double max, double first, double last, int points) {
            this.min = min; this.max = max; this.first = first; this.last = last; this.points = points;
            this.deltaPct = (first == 0.0) ? 0.0 : ((last - first) / first) * 100.0;
        }
    }

    public static Stats stats(List<Double> vals) {
        if (vals == null || vals.isEmpty()) return new Stats(0,0,0,0,0);
        double min = vals.stream().min(Double::compare).orElse(0.0);
        double max = vals.stream().max(Double::compare).orElse(0.0);
        double first = vals.get(0);
        double last = vals.get(vals.size()-1);
        return new Stats(min, max, first, last, vals.size());
    }

    public static String sparkline(List<Double> vals, String glyphs) {
        if (vals == null || vals.isEmpty()) return "";
        double min = vals.stream().min(Double::compare).orElse(0.0);
        double max = vals.stream().max(Double::compare).orElse(0.0);
        if (glyphs == null || glyphs.isEmpty()) glyphs = "▁▂▃▄▅▆▇█";
        StringBuilder sb = new StringBuilder();
        for (double v : vals) {
            int idx = (max == min) ? 0 : (int) Math.floor((v - min) / (max - min) * (glyphs.length() - 1));
            idx = Math.max(0, Math.min(idx, glyphs.length() - 1));
            sb.append(glyphs.charAt(idx));
        }
        return sb.toString();
    }

    public static String fmt2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    public static List<Double> toDoubleList(List<? extends Number> ns) {
        if (ns == null) return Collections.emptyList();
        List<Double> out = new ArrayList<>(ns.size());
        for (Number n : ns) out.add(n.doubleValue());
        return out;
    }
}
