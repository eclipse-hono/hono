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

import io.micrometer.core.instrument.Tag;

/**
 * Metrics reported by the Authentication Server.
 *
 */
public interface AuthenticationServerMetrics {

    /**
     * The name of the tag to use for indicating the type of client.
     */
    String TAG_NAME_CLIENT_TYPE = "client-type";

    /**
     * The outcome of an attempt to authenticate a client.
     * <p>
     * The immutable enum check is disabled because the offending field's (tag) type
     * is in fact immutable but has no corresponding annotation.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    enum ClientType {
        AUTH_SERVICE("auth-service"),
        DISPATCH_ROUTER("dispatch-router"),
        UNKNOWN("unknown");

        private final Tag tag;

        ClientType(final String tagValue) {
            this.tag = Tag.of(TAG_NAME_CLIENT_TYPE, tagValue);
        }

        /**
         * Gets a <em>Micrometer</em> tag for the client type.
         *
         * @return The tag.
         */
        public Tag asTag() {
            return tag;
        }
    }

    /**
     * Reports the outcome of a client of unknown type's attempt to establish a connection
     * to the Authentication Server.
     *
     * @param outcome The outcome of the connection attempt.
     * @throws NullPointerException if outcome is {@code null}.
     */
    void reportConnectionAttempt(AuthenticationAttemptOutcome outcome);

    /**
     * Reports the outcome of a client's attempt to establish a connection
     * to the Authentication Server.
     *
     * @param outcome The outcome of the connection attempt.
     * @param clientType The type of client that made the attempt.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    void reportConnectionAttempt(AuthenticationAttemptOutcome outcome, ClientType clientType);
}
