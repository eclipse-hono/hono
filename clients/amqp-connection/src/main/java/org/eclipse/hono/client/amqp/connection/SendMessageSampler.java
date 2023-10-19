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

package org.eclipse.hono.client.amqp.connection;

import org.apache.qpid.proton.amqp.transport.DeliveryState;

/**
 * An interface for sampling <em>send message</em> operations.
 */
public interface SendMessageSampler {

    /**
     * A factory for creating samplers.
     */
    interface Factory {

        /**
         * Creates a new sampler.
         *
         * @param messageType The message type to create a sampler for.
         * @return A new sampler.
         * @throws NullPointerException if message type is {@code null}.
         */
        SendMessageSampler create(String messageType);

        /**
         * Gets a default, no-op implementation.
         *
         * @return A no-op implementation, never returns {@code null}.
         */
        static SendMessageSampler.Factory noop() {
            return Noop.FACTORY;
        }

    }

    /**
     * Gets a default, no-op implementation.
     *
     * @return A no-op implementation, never returns {@code null}.
     */
    static SendMessageSampler noop() {
        return Noop.SAMPLER;
    }

    /**
     * A default no-op implementation.
     */
    class Noop {

        private static final Sample SAMPLE = new Sample() {

            @Override
            public void completed(final String outcome) {
                // nothing to do
            }

            @Override
            public void timeout() {
                // nothing to do
            }

        };

        private static final SendMessageSampler SAMPLER = new SendMessageSampler() {

            @Override
            public Sample start(final String tenantId) {
                return SAMPLE;
            }

            @Override
            public void noCredit(final String tenantId) {
                // nothing to do
            }

        };

        private static final Factory FACTORY = (String messageType) -> SAMPLER;

        private Noop() {
        }

    }

    /**
     * An active sample instance.
     */
    interface Sample {

        String OUTCOME_ABORTED = "aborted";

        /**
         * Marks the sampling operation as completed. To be called when the message was processed by the remote peer.
         *
         * @param outcome The outcome of the message. This is expected to be one of AMQP dispositions.
         */
        void completed(String outcome);

        /**
         * Marks the sampling operation as completed. To be called when the message was processed by the remote peer.
         * <p>
         * This method will use the <em>simple class name</em> of the delivery state parameter. If the
         * parameter is {@code null} the operating is considered <em>aborted</em> and the value {@link #OUTCOME_ABORTED}
         * will be used instead.
         *
         * @param remoteState The remote state.
         */
        default void completed(final DeliveryState remoteState) {
            if (remoteState == null) {
                completed(OUTCOME_ABORTED);
            } else {
                completed(remoteState.getClass().getSimpleName().toLowerCase());
            }
        }

        /**
         * To be called when sending the message timed out.
         */
        void timeout();

    }

    /**
     * Starts the sampling operation.
     *
     * @param tenantId The tenant ID to sample for. If {@code null} or an empty string,
     *                 the value <em>UNKNOWN</em> will be used as the tenant identifier.
     * @return A sample instance.
     */
    Sample start(String tenantId);

    /**
     * Records a case when there are no credits for sending a message.
     *
     * @param tenantId The tenant ID to sample for. If {@code null} or an empty string,
     *                 the value <em>UNKNOWN</em> will be used as the tenant identifier.
     */
    void noCredit(String tenantId);

}
