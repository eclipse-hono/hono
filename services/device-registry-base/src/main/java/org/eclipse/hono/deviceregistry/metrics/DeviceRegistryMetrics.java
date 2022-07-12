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

package org.eclipse.hono.deviceregistry.metrics;

/**
 * Metrics for the Device Registry service.
 */
public interface DeviceRegistryMetrics {

    /**
     * The name of the meter holding the total number of registered tenants.
     */
    String TOTAL_TENANTS_METRIC_KEY = "hono.tenants.total";
}
