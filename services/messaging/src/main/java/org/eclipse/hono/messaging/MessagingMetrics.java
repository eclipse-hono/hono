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

package org.eclipse.hono.messaging;

import org.eclipse.hono.service.metric.Metrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for Hono Messaging.
 */
@Component
public interface MessagingMetrics extends Metrics {

    /**
     * Reports a newly established connection to the downstream
     * AMQP 1.0 Messaging Network.
     */
    void incrementDownStreamConnections();

    /**
     * Reports a connection to the downstream AMQP 1.0 Messaging Network
     * being closed.
     */
    void decrementDownStreamConnections();

    /**
     * 
     * @param address The link's target address.
     * @param credits The number of credits.
     */
    void submitDownstreamLinkCredits(String address, double credits);

    /**
     * Reports a newly established sender link to the downstream
     * AMQP 1.0 Messaging Network.
     * 
     * @param address The link's target address.
     */
    void incrementDownstreamSenders(String address);

    /**
     * Reports a sender link to the downstream AMQP 1.0 Messaging Network
     * being closed.
     * 
     * @param address The link's target address.
     */
    void decrementDownstreamSenders(String address);

    /**
     * Reports a newly established receiver link to an upstream protocol
     * adapter.
     * 
     * @param address The link's target address.
     */
    void incrementUpstreamLinks(String address);

    /**
     * Reports a receiver link to an upstream protocol adapter
     * being closed.
     * 
     * @param address The link's target address.
     */
    void decrementUpstreamLinks(String address);

    /**
     * Reports a message having been discarded.
     * 
     * @param address The message's address.
     */
    void incrementDiscardedMessages(String address);

    /**
     * Reports a message having been processed.
     * 
     * @param address The message's address.
     */
    void incrementProcessedMessages(String address);

    /**
     * Reports a message as being <em>undeliverable</em>.
     * 
     * @param address The message's address.
     */
    void incrementUndeliverableMessages(String address);
}
