/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.junit.jupiter.api.Test;


/**
 * Verifies behavior of {@link ResourceLimitsPeriod}.
 *
 */
public class ResourceLimitsPeriodTest {

    /**
     * Verifies the resource usage period calculation for various scenarios.
     *
     */
    @Test
    public void verifyElapsedAccountingPeriodDurationCalculation() {

        // Monthly mode

        // within initial accounting period
        Instant since = Instant.parse("2019-09-06T07:15:00Z");
        Instant now = Instant.parse("2019-09-10T14:30:00Z");
        // most recent accounting period starts at effective since
        Instant currentAccountingPeriodStart = since;

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                new ResourceLimitsPeriod(PeriodMode.monthly).getElapsedAccountingPeriodDuration(since, now));

        // after initial accounting period
        since = Instant.parse("2019-08-06T05:11:00Z");
        now = Instant.parse("2019-09-06T14:30:00Z");
        // current accounting period starts on first day of current month at midnight (start of day) UTC
        currentAccountingPeriodStart = Instant.parse("2019-09-01T00:00:00Z");

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                new ResourceLimitsPeriod(PeriodMode.monthly).getElapsedAccountingPeriodDuration(since, now));

        // Days mode

        // within initial accounting period
        since = Instant.parse("2019-08-20T19:03:59Z");
        now = Instant.parse("2019-09-10T09:32:17Z");
        // current accounting period starts at effective since date-time
        currentAccountingPeriodStart = since;

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                new ResourceLimitsPeriod(PeriodMode.days)
                    .setNoOfDays(30)
                    .getElapsedAccountingPeriodDuration(since, now));

        // after initial accounting period
        since = Instant.parse("2019-07-03T11:49:22Z");
        now = Instant.parse("2019-09-10T14:30:11Z");
        // current accounting period starts 4 x 15 days after start
        currentAccountingPeriodStart = Instant.parse("2019-09-01T11:49:22Z");

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                new ResourceLimitsPeriod(PeriodMode.days)
                    .setNoOfDays(15)
                    .getElapsedAccountingPeriodDuration(since, now));
    }

}
