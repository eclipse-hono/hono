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
public class MessagingMetrics extends Metrics {

    private static final String SERVICE_PREFIX = "hono.messaging";

    private static final String CONNECTIONS_DOWNSTREAM   = ".connections.downstream";
    private static final String LINK_DOWNSTREAM_CREDITS  = ".link.downstream.credits.";
    private static final String SENDERS_DOWNSTREAM       = ".senders.downstream.";
    private static final String RECEIVERS_UPSTREAM_LINKS = ".receivers.upstream.links.";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }

    void incrementDownStreamConnections() {
        counterService.increment(SERVICE_PREFIX + CONNECTIONS_DOWNSTREAM);
    }

    void decrementDownStreamConnections() {
        counterService.decrement(SERVICE_PREFIX + CONNECTIONS_DOWNSTREAM);
    }

    void submitDownstreamLinkCredits(final String address, final double credits) {
        gaugeService.submit(SERVICE_PREFIX + LINK_DOWNSTREAM_CREDITS + normalizeAddress(address), credits);
    }

    void incrementDownstreamSenders(final String address) {
        counterService.increment(SERVICE_PREFIX + SENDERS_DOWNSTREAM + normalizeAddress(address));
    }

    void decrementDownstreamSenders(final String address) {
        counterService.decrement(SERVICE_PREFIX + SENDERS_DOWNSTREAM + normalizeAddress(address));
    }

    void incrementUpstreamLinks(final String address) {
        counterService.increment(SERVICE_PREFIX + RECEIVERS_UPSTREAM_LINKS + normalizeAddress(address));
    }
    void decrementUpstreamLinks(final String address) {
        counterService.decrement(SERVICE_PREFIX + RECEIVERS_UPSTREAM_LINKS + normalizeAddress(address));
    }

    void incrementDiscardedMessages(final String address) {
        counterService.increment(SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + DISCARDED);
    }

    void incrementProcessedMessages(final String address) {
        counterService.increment(METER_PREFIX + SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + PROCESSED);
    }

    void incrementUndeliverableMessages(final String address) {
        counterService.increment(SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + UNDELIVERABLE);
    }
}
