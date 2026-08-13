package org.phantam.fozminespoofcore.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeTest {

    @Test
    @DisplayName("Should parse valid range string correctly")
    void testParseValidRange() {
        Range range = Range.parse("1.5-3.5", 1.0, 5.0);
        assertEquals(1.5, range.getMin(), 0.001);
        assertEquals(3.5, range.getMax(), 0.001);
    }

    @Test
    @DisplayName("Should parse single value as min == max")
    void testParseSingleValue() {
        Range range = Range.parse("2.5", 1.0, 5.0);
        assertEquals(2.5, range.getMin(), 0.001);
        assertEquals(2.5, range.getMax(), 0.001);
    }

    @Test
    @DisplayName("Should fallback to default on invalid or null format")
    void testParseInvalidFallback() {
        Range rangeNull = Range.parse(null, 1.0, 5.0);
        assertEquals(1.0, rangeNull.getMin(), 0.001);
        assertEquals(5.0, rangeNull.getMax(), 0.001);

        Range rangeInvalid = Range.parse("invalid-string", 2.0, 4.0);
        assertEquals(2.0, rangeInvalid.getMin(), 0.001);
        assertEquals(4.0, rangeInvalid.getMax(), 0.001);
    }

    @Test
    @DisplayName("Should convert random seconds into correct ticks (1s = 20 ticks)")
    void testRandomTicks() {
        Range range = Range.parse("2.0-2.0", 1.0, 1.0); // Exact 2.0s
        assertEquals(40L, range.getRandomTicks()); // 2.0 * 20 = 40 ticks
    }
}