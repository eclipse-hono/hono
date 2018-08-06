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

import org.eclipse.hono.service.metric.DropwizardBasedMetrics;
import org.springframework.stereotype.Component;

/**
 * Dropwizard based metrics for Hono Messaging.
 */
@Component
public class DropwizardBasedMessagingMetrics extends DropwizardBasedMetrics implements MessagingMetrics {

    private static final String SERVICE_PREFIX = "hono.messaging";

    private static final String CONNECTIONS_DOWNSTREAM   = ".connections.downstream";
    private static final String LINK_DOWNSTREAM_CREDITS  = ".link.downstream.credits.";
    private static final String SENDERS_DOWNSTREAM       = ".senders.downstream.";
    private static final String RECEIVERS_UPSTREAM_LINKS = ".receivers.upstream.links.";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }

    @Override
    public final void incrementDownStreamConnections() {
        counterService.increment(SERVICE_PREFIX + CONNECTIONS_DOWNSTREAM);
    }

    @Override
    public final void decrementDownStreamConnections() {
        counterService.decrement(SERVICE_PREFIX + CONNECTIONS_DOWNSTREAM);
    }

    @Override
    public final void submitDownstreamLinkCredits(final String address, final double credits) {
        gaugeService.submit(SERVICE_PREFIX + LINK_DOWNSTREAM_CREDITS + normalizeAddress(address), credits);
    }

    @Override
    public final void incrementDownstreamSenders(final String address) {
        counterService.increment(SERVICE_PREFIX + SENDERS_DOWNSTREAM + normalizeAddress(address));
    }

    @Override
    public final void decrementDownstreamSenders(final String address) {
        counterService.decrement(SERVICE_PREFIX + SENDERS_DOWNSTREAM + normalizeAddress(address));
    }

    @Override
    public final void incrementUpstreamLinks(final String address) {
        counterService.increment(SERVICE_PREFIX + RECEIVERS_UPSTREAM_LINKS + normalizeAddress(address));
    }

    @Override
    public final void decrementUpstreamLinks(final String address) {
        counterService.decrement(SERVICE_PREFIX + RECEIVERS_UPSTREAM_LINKS + normalizeAddress(address));
    }

    @Override
    public final void incrementDiscardedMessages(final String address) {
        counterService.increment(SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + DISCARDED);
    }

    @Override
    public final void incrementProcessedMessages(final String address) {
        counterService.increment(METER_PREFIX + SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + PROCESSED);
    }

    @Override
    public final void incrementUndeliverableMessages(final String address) {
        counterService.increment(SERVICE_PREFIX + MESSAGES + normalizeAddress(address) + UNDELIVERABLE);
    }
}
