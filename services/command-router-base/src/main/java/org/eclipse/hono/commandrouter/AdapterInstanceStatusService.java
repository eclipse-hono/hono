/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter;

import org.eclipse.hono.deviceconnection.infinispan.client.AdapterInstanceStatusProvider;
import org.eclipse.hono.util.AdapterInstanceStatus;
import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Future;

/**
 * A service for determining the status of adapter instances.
 */
public interface AdapterInstanceStatusService extends AdapterInstanceStatusProvider, Lifecycle {

    UnknownStatusProvidingService UNKNOWN_STATUS_PROVIDING_SERVICE = new UnknownStatusProvidingService();

    /**
     * Status service that always returns the {@link AdapterInstanceStatus#UNKNOWN} status.
     */
    class UnknownStatusProvidingService implements AdapterInstanceStatusService {

        @Override
        public AdapterInstanceStatus getStatus(final String adapterInstanceId) {
            return AdapterInstanceStatus.UNKNOWN;
        }

        @Override
        public Future<Void> start() {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> stop() {
            return Future.succeededFuture();
        }
    }
}
