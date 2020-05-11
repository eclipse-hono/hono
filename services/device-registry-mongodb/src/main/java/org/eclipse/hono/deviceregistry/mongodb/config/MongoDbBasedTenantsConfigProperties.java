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
package org.eclipse.hono.deviceregistry.mongodb.config;

/**
 * Configuration properties for Hono's tenant service and management APIs.
 */
public final class MongoDbBasedTenantsConfigProperties extends AbstractMongoDbBasedRegistryConfigProperties {

    /**
     * Default name of mongodb tenant collection.
     */
    private static final String DEFAULT_TENANTS_COLLECTION_NAME = "tenants";

    @Override
    protected String getDefaultCollectionName() {
        return DEFAULT_TENANTS_COLLECTION_NAME;
    }

}
