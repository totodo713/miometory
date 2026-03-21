package com.worklog.domain.monthlyperiod;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MonthlyPeriodRuleId")
class MonthlyPeriodRuleIdTest {

    @Test
    @DisplayName("should create from UUID")
    void shouldCreateFromUuid() {
        UUID uuid = UUID.randomUUID();
        MonthlyPeriodRuleId id = MonthlyPeriodRuleId.of(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    @DisplayName("should create from String")
    void shouldCreateFromString() {
        UUID uuid = UUID.randomUUID();
        MonthlyPeriodRuleId id = MonthlyPeriodRuleId.of(uuid.toString());
        assertEquals(uuid, id.value());
    }

    @Test
    @DisplayName("should generate random ID")
    void shouldGenerate() {
        MonthlyPeriodRuleId id = MonthlyPeriodRuleId.generate();
        assertNotNull(id.value());
    }

    @Test
    @DisplayName("should reject null value")
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new MonthlyPeriodRuleId(null));
    }

    @Test
    @DisplayName("toString should return UUID string")
    void toStringShouldReturnUuidString() {
        UUID uuid = UUID.randomUUID();
        MonthlyPeriodRuleId id = MonthlyPeriodRuleId.of(uuid);
        assertEquals(uuid.toString(), id.toString());
    }

    @Test
    @DisplayName("should support equality")
    void shouldSupportEquality() {
        UUID uuid = UUID.randomUUID();
        MonthlyPeriodRuleId id1 = MonthlyPeriodRuleId.of(uuid);
        MonthlyPeriodRuleId id2 = MonthlyPeriodRuleId.of(uuid);
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
