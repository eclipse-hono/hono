/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.service.metric.Metrics;
import org.eclipse.hono.service.metric.NoopBasedMetrics;

/**
 * Metrics for the MQTT adapter.
 */
public interface MqttAdapterMetrics extends Metrics {

    /**
     * A no-op implementation this specific metrics type.
     */
    final class Noop extends NoopBasedMetrics implements MqttAdapterMetrics {

        private Noop() {
        }

        @Override
        public void incrementActivePersistentSessions() {
            // NOOP
        }

        @Override
        public void decrementActivePersistentSessions() {
            // NOOP
        }

        @Override
        public void reportSessionExpired() {
            // NOOP
        }

        @Override
        public void reportSessionResumed() {
            // NOOP
        }

        @Override
        public void reportClientMessageReceivedWithExpiry() {
            // NOOP
        }

        @Override
        public void reportCommandExpiredBeforeDelivery() {
            // NOOP
        }

        @Override
        public void reportCommandSentWithExpiry() {
            // NOOP
        }
    }

    /**
     * The no-op implementation.
     */
    MqttAdapterMetrics NOOP = new Noop();

    /**
     * Increments the number of currently active persistent sessions.
     */
    void incrementActivePersistentSessions();

    /**
     * Decrements the number of currently active persistent sessions.
     */
    void decrementActivePersistentSessions();

    /**
     * Reports that a persistent session has expired.
     */
    void reportSessionExpired();

    /**
     * Reports that a persistent session has been resumed.
     */
    void reportSessionResumed();

    /**
     * Reports that a message with an expiry interval has been received from a client.
     */
    void reportClientMessageReceivedWithExpiry();

    /**
     * Reports that a command message has expired before it could be delivered to the client.
     */
    void reportCommandExpiredBeforeDelivery();

    /**
     * Reports that a command message with an expiry interval has been sent to a client.
     */
    void reportCommandSentWithExpiry();
}
