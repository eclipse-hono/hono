/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl.amqp;

import java.util.Objects;

import org.eclipse.hono.client.command.amqp.ProtonBasedCommand;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandContext;
import org.eclipse.hono.commandrouter.impl.AbstractCommandProcessingQueue;

import io.vertx.core.Vertx;

/**
 * Queue with the commands currently being processed.
 * <p>
 * The final step of processing a command, forwarding it to its target, is invoked here maintaining the
 * order of the incoming commands.
 * <p>
 * Command order is maintained here across commands targeted at the same tenant/device. This is done by means
 * of keeping different (sub-)queues, referenced by a queue key derived from the command tenant and the hash code
 * of the command target device identifier.
 */
public class ProtonBasedCommandProcessingQueue
        extends AbstractCommandProcessingQueue<ProtonBasedCommandContext, ProtonBasedCommandProcessingQueue.TenantAndDeviceHashQueueKey> {

    private static final int NUM_QUEUES_PER_TENANT = 8;

    /**
     * Creates a new ProtonBasedCommandProcessingQueue.
     *
     * @param vertx The vert.x instance to use.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public ProtonBasedCommandProcessingQueue(final Vertx vertx) {
        super(vertx);
    }

    @Override
    protected TenantAndDeviceHashQueueKey getQueueKey(final ProtonBasedCommandContext commandContext) {
        final ProtonBasedCommand command = commandContext.getCommand();
        return new TenantAndDeviceHashQueueKey(command.getTenant(), command.getDeviceId());
    }

    @Override
    protected String getCommandSourceForLog(final TenantAndDeviceHashQueueKey queueKey) {
        return "address for tenant [" + queueKey.getTenantId() + "]";
    }

    /**
     * Removes all queue entries related to the given tenant.
     * @param tenantId The tenant identifier.
     */
    public void removeEntriesForTenant(final String tenantId) {
        removeCommandQueueEntries(queueKey -> queueKey.getTenantId().equals(tenantId));
    }

    /**
     * Represents the key used for the queues map.
     * Based on tenant identifier and an index derived from the device identifier hashcode.
     */
    static final class TenantAndDeviceHashQueueKey {
        final String tenantId;
        final int queueIndex;

        TenantAndDeviceHashQueueKey(final String tenantId, final String deviceId) {
            this.tenantId = Objects.requireNonNull(tenantId);
            Objects.requireNonNull(deviceId);
            this.queueIndex = deviceId.hashCode() % NUM_QUEUES_PER_TENANT;
        }

        /**
         * Gets the tenant identifier.
         * @return The tenant identifier.
         */
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!getClass().isInstance(o)) {
                return false;
            }
            final TenantAndDeviceHashQueueKey that = (TenantAndDeviceHashQueueKey) o;
            return queueIndex == that.queueIndex && tenantId.equals(that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, queueIndex);
        }
    }
}
