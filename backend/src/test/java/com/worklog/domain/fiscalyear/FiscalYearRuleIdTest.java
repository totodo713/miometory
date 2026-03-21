package com.worklog.domain.fiscalyear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FiscalYearRuleId")
class FiscalYearRuleIdTest {

    @Test
    @DisplayName("should create from UUID")
    void shouldCreateFromUuid() {
        UUID uuid = UUID.randomUUID();
        FiscalYearRuleId id = FiscalYearRuleId.of(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    @DisplayName("should create from String")
    void shouldCreateFromString() {
        UUID uuid = UUID.randomUUID();
        FiscalYearRuleId id = FiscalYearRuleId.of(uuid.toString());
        assertEquals(uuid, id.value());
    }

    @Test
    @DisplayName("should generate random ID")
    void shouldGenerate() {
        FiscalYearRuleId id = FiscalYearRuleId.generate();
        assertNotNull(id.value());
    }

    @Test
    @DisplayName("should reject null value")
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new FiscalYearRuleId(null));
    }

    @Test
    @DisplayName("toString should return UUID string")
    void toStringShouldReturnUuidString() {
        UUID uuid = UUID.randomUUID();
        FiscalYearRuleId id = FiscalYearRuleId.of(uuid);
        assertEquals(uuid.toString(), id.toString());
    }

    @Test
    @DisplayName("should support equality")
    void shouldSupportEquality() {
        UUID uuid = UUID.randomUUID();
        FiscalYearRuleId id1 = FiscalYearRuleId.of(uuid);
        FiscalYearRuleId id2 = FiscalYearRuleId.of(uuid);
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
