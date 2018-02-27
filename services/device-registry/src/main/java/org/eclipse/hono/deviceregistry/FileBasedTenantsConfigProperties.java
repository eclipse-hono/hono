/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.deviceregistry;

/**
 * Configuration properties for Hono's tenant API as own server.
 *
 */
public final class FileBasedTenantsConfigProperties extends AbstractFileBasedRegistryConfigProperties {

    private static final String DEFAULT_TENANTS_FILENAME = "/var/lib/hono/device-registry/tenants.json";

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDefaultFileName() {
        return DEFAULT_TENANTS_FILENAME;
    }

}
