package com.hhs.service.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the human-readable sentences that disclose a substituted default value.
 *
 * <p>This is a plain mutable value object threaded explicitly through the scorer chain, not a
 * Spring bean. That is deliberate: {@code service/domain/} is Spring-free pure logic, and
 * {@code HealthReportServiceImpl} calls {@code ScoreCalculator} outside any request, so a
 * request-scoped bean or a {@code ThreadLocal} would be either unavailable or leak between
 * users on that path.</p>
 *
 * <p>{@link #add(String)} must be called inside the very branch that assigns the default, so
 * the disclosure cannot drift from the behaviour it describes. Entries are de-duplicated and
 * kept in insertion order, so the same substitution reached twice yields one sentence.</p>
 */
public final class AssumptionCollector {

    private final Set<String> items = new LinkedHashSet<>();

    /**
     * Create a no-argument collector.
     *
     * <p>The no-arg constructor is public but ordinary: every collector drops its entries once
     * the score payload has been built, so there is no shared or long-lived instance.</p>
     */
    public AssumptionCollector() {
    }

    /**
     * Create a collector that no caller will read.
     *
     * <p>Used by the legacy signatures so an existing caller sees byte-identical behaviour:
     * it pays for an empty set and nothing else.</p>
     *
     * @return a new empty collector
     */
    public static AssumptionCollector noop() {
        return new AssumptionCollector();
    }

    /**
     * Record one substitution sentence.
     *
     * <p>Blank and {@code null} input is ignored, so a branch may pass a value straight through
     * without pre-checking it.</p>
     *
     * @param humanReadable the user-facing Chinese sentence, possibly {@code null}
     */
    public void add(String humanReadable) {
        if (humanReadable != null && !humanReadable.isBlank()) {
            items.add(humanReadable);
        }
    }

    /**
     * Snapshot the collected sentences.
     *
     * @return an immutable copy in insertion order; never {@code null}
     */
    public List<String> toList() {
        return List.copyOf(items);
    }

    /**
     * Report whether any sentence was recorded.
     *
     * @return true when this collector is empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}