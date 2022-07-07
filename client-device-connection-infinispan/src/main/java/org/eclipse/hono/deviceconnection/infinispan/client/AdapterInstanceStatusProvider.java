/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Set;

import org.eclipse.hono.util.AdapterInstanceStatus;

import io.vertx.core.Future;

/**
 * Provides the status of an adapter instance.
 */
public interface AdapterInstanceStatusProvider {

    /**
     * Gets the status of the adapter identified by the given identifier.
     *
     * @param adapterInstanceId The identifier of the adapter instance.
     * @return The status of the adapter instance.
     * @throws NullPointerException if adapterInstanceId is {@code null}.
     */
    AdapterInstanceStatus getStatus(String adapterInstanceId);

    /**
     * Gets the identifiers of the adapter instances from the given collection
     * that have the {@link AdapterInstanceStatus#DEAD} status.
     * <p>
     * Compared to {@link #getStatus(String)}, extra measures may be taken here
     * to resolve the status of adapter instances otherwise classified as
     * {@link AdapterInstanceStatus#SUSPECTED_DEAD} before completing the result future.
     *
     * @param adapterInstanceIds The identifiers of the adapter instances.
     * @return A succeeded future containing the identifiers of the dead adapter instances or a failed future
     *         indicating the reason why the operation failed.
     * @throws NullPointerException if adapterInstanceIds is {@code null}.
     */
    Future<Set<String>> getDeadAdapterInstances(Collection<String> adapterInstanceIds);
}
