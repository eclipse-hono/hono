/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.metric;

/**
 * A no-op metrics implementation.
 */
public class NoopBasedMetrics implements Metrics {

    protected NoopBasedMetrics() {
    }

    @Override
    public void incrementUndeliverableMessages(final String type, final String tenantId) {
    }

    @Override
    public void incrementUnauthenticatedConnections() {
    }

    @Override
    public void incrementProcessedPayload(final String type, final String tenantId, final long payloadSize) {
    }

    @Override
    public void incrementProcessedMessages(final String type, final String tenantId) {
    }

    @Override
    public void incrementNoCommandReceivedAndTTDExpired(final String tenantId) {
    }

    @Override
    public void incrementConnections(final String tenantId) {
    }

    @Override
    public void incrementCommandResponseDeliveredToApplication(final String tenantId) {
    }

    @Override
    public void incrementCommandDeliveredToDevice(final String tenantId) {
    }

    @Override
    public void decrementUnauthenticatedConnections() {
    }

    @Override
    public void decrementConnections(final String tenantId) {
    }
}
