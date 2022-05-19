/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.adapter.amqp;

/**
 * Represent constants used throughout the Amqp adapter code base.
 */
public final class AmqpAdapterConstants {

    /**
     * The key that the name of the connection's TLS cipher suite is stored under in a
     * {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_TLS_CIPHER_SUITE = "TLS_CIPHER_SUITE";
    /**
     * The key that an authenticated client of a protocol adapter (representing a device)
     * is stored under in a {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_CLIENT_DEVICE = "CLIENT_DEVICE";
    /**
     * The key that an OpenTracing span created by the AMQP Adapter's SASL authenticator
     * is stored under in a {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_CURRENT_SPAN = "CURRENT_SPAN";

    /**
     * The key that the trace sampling priority determined for a request tenant/auth-id
     * is stored under in a {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_TRACE_SAMPLING_PRIORITY = "TRACE_SAMPLING_PRIORITY";

    private AmqpAdapterConstants() {
        // avoid instantiation
    }
}
