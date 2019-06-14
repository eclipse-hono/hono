/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.registry;

import org.eclipse.hono.tests.IntegrationTestSupport;

/**
 * A base class for implementing integration tests for
 * Tenant, Device Registration and Credentials service implementations.
 *
 */
abstract class DeviceRegistryTestBase {

    /**
     * Gets the helper to use for running tests.
     * 
     * @return The helper.
     */
    protected abstract IntegrationTestSupport getHelper();

}
