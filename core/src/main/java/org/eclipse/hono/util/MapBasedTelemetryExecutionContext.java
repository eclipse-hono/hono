/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

import org.eclipse.hono.auth.Device;

import io.opentracing.Span;

/**
 * An execution context that stores properties in a {@code Map}.
 *
 */
public abstract class MapBasedTelemetryExecutionContext extends MapBasedExecutionContext implements TelemetryExecutionContext {

    private final Device authenticatedDevice;

    /**
     * Creates a new context for a message received from a device.
     *
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @param authenticatedDevice The authenticated device that has uploaded the message or {@code null} if the device
     *            has not been authenticated.
     * @throws NullPointerException If span is {@code null}.
     */
    public MapBasedTelemetryExecutionContext(final Span span, final Device authenticatedDevice) {
        super(span);
        this.authenticatedDevice = authenticatedDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }
}
