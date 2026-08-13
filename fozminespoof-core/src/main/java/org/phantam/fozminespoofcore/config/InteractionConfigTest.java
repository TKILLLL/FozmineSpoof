package org.phantam.fozminespoofcore.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InteractionConfigTest {

    @Test
    @DisplayName("Should match plain text triggers with wildcards")
    void testWildcardTriggerMatching() {
        InteractionConfig config = new InteractionConfig(
                "greeting",
                List.of("hello*", "xin chao*"),
                1.0, 10, 10, 1, "1.0-2.0", "00:00-23:59",
                List.of("Hi!"), false, 0.85, "1.0", "2.0"
        );

        assertTrue(config.matches("hello world"));
        assertTrue(config.matches("xin chao cac ban"));
        assertFalse(config.matches("goodbye world"));
    }

    @Test
    @DisplayName("Should match regex pattern triggers")
    void testRegexTriggerMatching() {
        InteractionConfig config = new InteractionConfig(
                "shop",
                List.of("\\b(?:how|where)\\s+sell\\b"),
                1.0, 10, 10, 1, "1.0-2.0", "00:00-23:59",
                List.of("Use /shop"), true, 0.85, "1.0", "2.0"
        );

        assertTrue(config.matches("how sell items here"));
        assertTrue(config.matches("where sell diamonds"));
        assertFalse(config.matches("selling items now"));
    }
}