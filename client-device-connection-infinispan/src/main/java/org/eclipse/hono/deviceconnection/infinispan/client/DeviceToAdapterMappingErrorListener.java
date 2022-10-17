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

package org.eclipse.hono.deviceconnection.infinispan.client;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * Listener notified when an incorrect device to adapter mapping is found.
 */
public interface DeviceToAdapterMappingErrorListener {

    /**
     * Called when an obsolete device to adapter mapping is found.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param adapterInstanceId The adapter instance identifier.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The future will be succeeded if the listener is
     *         notified successfully.
     */
    Future<Void> onObsoleteEntryFound(String tenantId, String deviceId, String adapterInstanceId, Span span);
}
