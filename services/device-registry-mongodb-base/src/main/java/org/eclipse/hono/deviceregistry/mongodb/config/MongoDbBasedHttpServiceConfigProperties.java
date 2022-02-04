/**
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
 */


package org.eclipse.hono.deviceregistry.mongodb.config;

import org.eclipse.hono.service.http.HttpServiceConfigProperties;


/**
 * Configuration properties for the registry's HTTP endpoint.
 */
public final class MongoDbBasedHttpServiceConfigProperties extends HttpServiceConfigProperties {

    private MongoAuthProviderConfig authenticationProviderConfig = new MongoAuthProviderConfig();

    /**
     * Creates default properties.
     */
    public MongoDbBasedHttpServiceConfigProperties() {
        super();
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options are {@code null}.
     */
    public MongoDbBasedHttpServiceConfigProperties(final MongoDbBasedHttpServiceConfigOptions options) {
        super(options.commonOptions());
        this.authenticationProviderConfig = new MongoAuthProviderConfig(options.auth());
    }

    /**
     * Gets the configuration for the authentication provider guarding access to the registry's
     * HTTP endpoint.
     *
     * @return The configuration.
     */
    public MongoAuthProviderConfig getAuth() {
        return authenticationProviderConfig;
    }
}
