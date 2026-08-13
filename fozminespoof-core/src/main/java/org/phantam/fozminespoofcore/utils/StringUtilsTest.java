package org.phantam.fozminespoofcore.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.testng.AssertJUnit.assertEquals;

class StringUtilsTest {

    @Test
    @DisplayName("Should strip Vietnamese diacritics correctly")
    void testStripDiacritics() {
        assertEquals("Xin chao cac ban!", StringUtils.stripDiacritics("Xin chào các bạn!"));
        assertEquals("Thanh pho Ho Chi Minh", StringUtils.stripDiacritics("Thành phố Hồ Chí Minh"));
        assertEquals("Anh va Em", StringUtils.stripDiacritics("Anh và Em"));
    }

    @Test
    @DisplayName("Should clean raw message into normalized form")
    void testCleanMessage() {
        assertEquals("xin chao cac ban", StringUtils.cleanMessage("  Xin chào các bạn!!!  "));
        assertEquals("hello world", StringUtils.cleanMessage("Hello, World@#$!"));
        assertEquals("", StringUtils.cleanMessage(null));
        assertEquals("", StringUtils.cleanMessage("   "));
    }

    @ParameterizedTest
    @CsvSource({
            "kitten, sitting, 3",
            "hello, hello, 0",
            "abc, def, 3",
            "bot, bot123, 1", // bot vs bot
            "'', hello, 5"
    })
    @DisplayName("Should calculate correct Levenshtein distance")
    void testLevenshteinDistance(String s1, String s2, int expectedDistance) {
        assertEquals(expectedDistance, StringUtils.levenshteinDistance(s1, s2));
    }
}