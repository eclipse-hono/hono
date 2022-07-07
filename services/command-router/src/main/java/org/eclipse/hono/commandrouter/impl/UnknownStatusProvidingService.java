/**
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
 */


package org.eclipse.hono.commandrouter.impl;

import java.util.Collection;
import java.util.Set;

import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.util.AdapterInstanceStatus;

import io.vertx.core.Future;

/**
 * Status service that always returns the {@link AdapterInstanceStatus#UNKNOWN} status.
 */
public final class UnknownStatusProvidingService implements AdapterInstanceStatusService {

    @Override
    public AdapterInstanceStatus getStatus(final String adapterInstanceId) {
        return AdapterInstanceStatus.UNKNOWN;
    }

    @Override
    public Future<Set<String>> getDeadAdapterInstances(
            final Collection<String> adapterInstanceIds) {
        return Future.succeededFuture(Set.of());
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
