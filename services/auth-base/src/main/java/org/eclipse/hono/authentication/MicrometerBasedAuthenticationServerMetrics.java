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


package org.eclipse.hono.authentication;

import java.util.Objects;

import org.eclipse.hono.service.auth.AuthenticationService.AuthenticationAttemptOutcome;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;


/**
 * Micrometer based implementation of Authentication Server metrics.
 *
 */
public final class MicrometerBasedAuthenticationServerMetrics implements AuthenticationServerMetrics {

    private static final String METER_AUTHENTICATION_ATTEMPTS = "hono.authentication.attempts";

    private final MeterRegistry meterRegistry;

    /**
     * Creates metrics for a Micrometer registry.
     *
     * @param meterRegistry The meter registry.
     * @throws NullPointerException if meter registry is {@code null}.
     */
    public MicrometerBasedAuthenticationServerMetrics(final MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportConnectionAttempt(final AuthenticationAttemptOutcome outcome) {
        reportConnectionAttempt(outcome, ClientType.UNKNOWN);
    }

    @Override
    public void reportConnectionAttempt(
            final AuthenticationAttemptOutcome outcome,
            final ClientType clientType) {

        Objects.requireNonNull(outcome);
        Objects.requireNonNull(clientType);
        meterRegistry
            .counter(METER_AUTHENTICATION_ATTEMPTS, Tags.of(
                    Tag.of("outcome", outcome.toString()),
                    clientType.asTag()))
            .increment();
    }
}
