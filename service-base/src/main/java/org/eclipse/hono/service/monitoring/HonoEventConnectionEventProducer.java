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
package org.eclipse.hono.service.monitoring;

import org.eclipse.hono.client.HonoClient;

/**
 * A connection event producer based on the Hono <em>Event API</em>.
 */
public class HonoEventConnectionEventProducer extends AbstractMessageSenderConnectionEventProducer {

    /**
     * Create a new instance from a HonoClient instance.
     * 
     * @param deviceRegistryClient The client instance to use for contacting the device registry. Must not be
     *            {@code null}.
     * @param messageSenderClient The client instance to use for sending events. Must not be {@code null}.
     * @throws NullPointerException in the case any client is {@code null}.
     */
    public HonoEventConnectionEventProducer(final HonoClient deviceRegistryClient,
            final HonoClient messageSenderClient) {
        super(deviceRegistryClient, messageSenderClient, HonoClient::getOrCreateEventSender);
    }

}
