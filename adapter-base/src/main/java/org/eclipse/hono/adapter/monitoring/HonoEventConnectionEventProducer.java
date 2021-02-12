/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.monitoring;

/**
 * A connection event producer based on the Hono <em>Event API</em>.
 */
public final class HonoEventConnectionEventProducer extends AbstractMessageSenderConnectionEventProducer {

    /**
     * Create a new <em>connection event producer</em> based on the Hono <em>Event API</em>.
     */
    public HonoEventConnectionEventProducer() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Hono Event API based implementation";
    }
}
