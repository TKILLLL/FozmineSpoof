package org.phantam.fozminesproofapi.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for conditional debug logging.
 * Debug logs are only printed when the debug flag is enabled in config.
 */
public final class DebugLogger {

    private static boolean debugEnabled = false;

    private DebugLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Enables or disables debug logging globally.
     *
     * @param enabled true to enable debug logs
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Logs a debug message at INFO level if debug is enabled.
     *
     * @param logger  the logger to use
     * @param message the message to log
     */
    public static void log(Logger logger, String message) {
        if (debugEnabled) {
            logger.log(Level.INFO, "[DEBUG] " + message);
        }
    }

    /**
     * Logs a formatted debug message at INFO level if debug is enabled.
     *
     * @param logger the logger to use
     * @param format the format string
     * @param args   the arguments for the format string
     */
    public static void log(Logger logger, String format, Object... args) {
        if (debugEnabled) {
            logger.log(Level.INFO, "[DEBUG] " + String.format(format, args));
        }
    }

    /**
     * Logs a debug message at FINE level (less verbose) if debug is enabled.
     *
     * @param logger  the logger to use
     * @param message the message to log
     */
    public static void logFine(Logger logger, String message) {
        if (debugEnabled) {
            logger.log(Level.FINE, "[DEBUG] " + message);
        }
    }

    /**
     * Logs a formatted debug message at FINE level if debug is enabled.
     *
     * @param logger the logger to use
     * @param format the format string
     * @param args   the arguments for the format string
     */
    public static void logFine(Logger logger, String format, Object... args) {
        if (debugEnabled) {
            logger.log(Level.FINE, "[DEBUG] " + String.format(format, args));
        }
    }

    /**
     * Checks if debug is currently enabled.
     *
     * @return true if debug is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
}