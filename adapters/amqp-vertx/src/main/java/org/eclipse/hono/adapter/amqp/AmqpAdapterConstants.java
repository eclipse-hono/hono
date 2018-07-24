/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
     * They key that an authenticated client of a protocol adapter (representing a device)
     * is stored under in a {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_CLIENT_DEVICE = "CLIENT_DEVICE";

    private AmqpAdapterConstants() {
        // avoid instantiation
    }
}
