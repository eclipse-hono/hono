/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.auth.AuthenticationService.AuthenticationAttemptOutcome;

/**
 * A no-op implementation.
 *
 */
public final class NoopAuthenticationServerMetrics implements AuthenticationServerMetrics {

    /**
     * An instance to be shared.
     */
    public static final NoopAuthenticationServerMetrics INSTANCE = new NoopAuthenticationServerMetrics();

    private NoopAuthenticationServerMetrics() {
        // prevent instantiation
    }

    @Override
    public void reportConnectionAttempt(final AuthenticationAttemptOutcome outcome) {
        // do nothing
    }

    @Override
    public void reportConnectionAttempt(final AuthenticationAttemptOutcome outcome, final ClientType clientType) {
        // do nothing
    }
}
