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

package org.eclipse.hono.deviceconnection.infinispan.client;

import org.eclipse.hono.util.AdapterInstanceStatus;

/**
 * Provides the status of an adapter instance.
 */
public interface AdapterInstanceStatusProvider {

    AdapterInstanceStatusProvider UNKNOWN_STATUS_PROVIDER = new UnknownStatusProvider();

    /**
     * Gets the status of the adapter identified by the given identifier.
     *
     * @param adapterInstanceId The identifier of the adapter.
     * @return The status of the adapter instance.
     * @throws NullPointerException if adapterInstanceId is {@code null}.
     */
    AdapterInstanceStatus getStatus(String adapterInstanceId);

    /**
     * Status provider that always returns the {@link AdapterInstanceStatus#UNKNOWN} status.
     */
    class UnknownStatusProvider implements AdapterInstanceStatusProvider {

        @Override
        public AdapterInstanceStatus getStatus(final String adapterInstanceId) {
            return AdapterInstanceStatus.UNKNOWN;
        }
    }
}
