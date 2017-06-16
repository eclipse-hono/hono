/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.application;

import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.server.HonoServerConfigProperties;
import org.eclipse.hono.service.AbstractApplication;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Verticle;

/**
 * The Hono server main application class.
 * <p>
 * This class configures and wires up Hono's components (Verticles).
 * By default there will be as many instances of each verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
public class HonoApplication extends AbstractApplication<HonoServer, HonoServerConfigProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(HonoApplication.class);

    private AuthenticationService authenticationService;

    /**
     * @param authenticationService the authenticationService to set
     */
    @Autowired
    public final void setAuthenticationService(final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Deploys the additional service implementations that are
     * required by the HonoServer.
     *
     */
    @Override
    protected Future<Void> deployRequiredVerticles(final int maxInstances) {

        Future<Void> result = Future.future();

        Future<String> authFuture = deployAuthenticationService();// we only need 1 authentication service
        authFuture.setHandler(ar -> {
            if (ar.succeeded()) {
                result.complete();
            } else {
                result.fail(ar.cause());
            }
        });
        return result;
    }

    private Future<String> deployAuthenticationService() {
        Future<String> result = Future.future();
        if (!Verticle.class.isInstance(authenticationService)) {
            result.fail("authentication service is not a verticle");
        } else {
            LOG.info("Starting authentication service {}", authenticationService);
            getVertx().deployVerticle((Verticle) authenticationService, result.completer());
        }
        return result;
    }
}
