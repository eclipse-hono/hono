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

package org.eclipse.hono.service.auth;

import java.util.Objects;

import javax.net.ssl.TrustManagerFactory;

import org.eclipse.hono.client.HonoClient;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;


/**
 * Options for retrieving trust anchors from a Tenant service implementation.
 */
public class TenantApiTrustOptions implements TrustOptions {

    private final HonoTrustManagerFactory factory;

    private TenantApiTrustOptions(final HonoTrustManagerFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    /**
     * @param client The client for accessing the Tenant service.
     */
    public TenantApiTrustOptions(@Autowired final HonoClient client) {
        this(new HonoTrustManagerFactory(
                new TenantApiBasedX509TrustManager(Objects.requireNonNull(client))));
    }

    @Override
    public TrustOptions clone() {
        return new TenantApiTrustOptions(factory);
    }

    @Override
    public TrustManagerFactory getTrustManagerFactory(final Vertx vertx) {

        return factory;
    }
}
