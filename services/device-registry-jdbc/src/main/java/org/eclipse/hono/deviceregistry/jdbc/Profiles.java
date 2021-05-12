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

package org.eclipse.hono.deviceregistry.jdbc;

/**
 * Available profiles.
 */
public final class Profiles {

    public static final String PROFILE_REGISTRY_ADAPTER = "registry-adapter";
    public static final String PROFILE_REGISTRY_MANAGEMENT = "registry-management";
    public static final String PROFILE_TENANT_SERVICE = "tenant-service";
    public static final String PROFILE_CREATE_SCHEMA = "create-schema";

    private Profiles() {
    }

}
