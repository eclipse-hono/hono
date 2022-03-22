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

package org.eclipse.hono.deviceregistry.jdbc.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.deviceregistry.quarkus.AbstractHttpServerFactory;
import org.eclipse.hono.service.http.HttpServiceConfigOptions;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;

import io.smallrye.config.ConfigMapping;

/**
 * A factory for creating Device Registry Management API endpoints.
 *
 */
@ApplicationScoped
public class HttpServerFactory extends AbstractHttpServerFactory {

    private HttpServiceConfigProperties httpServerProperties;

    @Inject
    void setHttpServerProperties(
            @ConfigMapping(prefix = "hono.registry.http")
            final HttpServiceConfigOptions endpointOptions) {
        this.httpServerProperties = new HttpServiceConfigProperties(endpointOptions);
    }

    @Override
    protected final HttpServiceConfigProperties getHttpServerProperties() {
        return httpServerProperties;
    }
}
