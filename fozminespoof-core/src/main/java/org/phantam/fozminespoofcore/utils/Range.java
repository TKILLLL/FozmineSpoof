package org.phantam.fozminespoofcore.utils;

import java.util.concurrent.ThreadLocalRandom;

public final class Range {

    private final double min;
    private final double max;

    public Range(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    public static Range parse(String value, double defaultMin, double defaultMax) {
        if (value == null || value.isBlank()) {
            return new Range(defaultMin, defaultMax);
        }
        try {
            if (!value.contains("-")) {
                double v = Double.parseDouble(value.trim());
                return new Range(v, v);
            }
            String[] parts = value.split("-");
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            return new Range(min, max);
        } catch (NumberFormatException e) {
            return new Range(defaultMin, defaultMax);
        }
    }

    /**
     * Lấy ngẫu nhiên thời gian tính bằng Milliseconds (ms)
     */
    public long getRandomMillis() {
        if (min == max) return (long) (min * 1000.0);
        double randomSeconds = ThreadLocalRandom.current().nextDouble(min, max);
        return (long) (randomSeconds * 1000.0);
    }

    /**
     * Quy đổi ngẫu nhiên sang Ticks Minecraft (1 sec = 20 ticks)
     */
    public long getRandomTicks() {
        long ms = getRandomMillis();
        return Math.max(1L, ms / 50L);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
}