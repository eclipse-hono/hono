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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceProperties;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;

class JdbcBasedCredentialsServiceTest extends AbstractJdbcRegistryTest implements AbstractCredentialsServiceTest {
    private final CacheDirective expectedCacheDirective = CacheDirective.maxAgeDirective(new DeviceServiceProperties().getCredentialsTtl());

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheDirective getExpectedCacheDirective(final String credentialsType) {
        switch (credentialsType) {
            case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            case CredentialsConstants.SECRETS_TYPE_X509_CERT:
                return expectedCacheDirective;
            default:
                return CacheDirective.noCacheDirective();
        }
    }
}
