package com.worklog.application.service;

import java.math.BigDecimal;

/**
 * Result of resolving a member's effective standard daily hours.
 *
 * @param hours the resolved standard daily hours
 * @param source where the value came from: "member", "organization:uuid", "tenant", or "system"
 */
public record StandardHoursResolution(BigDecimal hours, String source) {}
