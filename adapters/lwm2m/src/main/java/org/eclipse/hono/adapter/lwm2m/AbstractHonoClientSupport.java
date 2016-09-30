/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m;

import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.client.HonoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.proton.ProtonClientOptions;

/**
 * Base class providing support for connecting to Hono.
 *
 */
public abstract class AbstractHonoClientSupport {

    protected Logger LOG = LoggerFactory.getLogger(getClass());
    protected String tenant;
    protected HonoClient hono;

    @PostConstruct
    public void connectToHono() throws IllegalStateException {

        hono.connect(getProtonClientOptions(), connectionAttempt -> {});
    }

    private ProtonClientOptions getProtonClientOptions() {
        return new ProtonClientOptions()
                .setReconnectAttempts(-1)
                .setReconnectInterval(200);
    }

    @Value("${hono.lwm2m.adapter.tenant:DEFAULT_TENANT}")
    public void setTenant(final String tenant) {
        this.tenant = Objects.requireNonNull(tenant);
    }

    @Autowired
    public void setHonoClient(final HonoClient client) {
        this.hono = Objects.requireNonNull(client);
    }
}
