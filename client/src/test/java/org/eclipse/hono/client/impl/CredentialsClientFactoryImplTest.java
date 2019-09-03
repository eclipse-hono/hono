/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoConnection;

import io.vertx.core.Future;

/**
 * Tests verifying the behavior of {@link CredentialsClientFactoryImpl}.
 */
public class CredentialsClientFactoryImplTest extends AbstractTenantTimeoutRelatedClientFactoryTest<CredentialsClient> {

    @Override
    protected Future<CredentialsClient> getClientFuture(final HonoConnection connection, final String tenantId) {
        return new CredentialsClientFactoryImpl(connection, null).getOrCreateCredentialsClient(tenantId);
    }
}
