/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.delegating;

import java.util.Objects;

import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;


/**
 * An authentication service that delegates authentication requests to a remote identity server.
 * <p>
 * This is the default authentication service for all Hono services.
 */
@Service
@Profile("!authentication-impl")
public class DelegatingAuthenticationService extends AbstractHonoAuthenticationService<AuthenticationServerClientConfigProperties> {

    private AuthenticationServerClient client;
    private ConnectionFactory factory;

    /**
     * Sets the factory to use for connecting to the authentication server.
     * 
     * @param connectionFactory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Autowired
    public void setConnectionFactory(@Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION) final ConnectionFactory connectionFactory) {
        this.factory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {
        if (factory == null) {
            startFuture.fail("no connection factory for authentication server set");
        } else {
            client = new AuthenticationServerClient(vertx, factory);
            startFuture.complete();
        }
    }

    @Override
    public void verifyExternal(final String authzid, final String subjectDn, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
        client.verifyExternal(authzid, subjectDn, authenticationResultHandler);
    }

    @Override
    public void verifyPlain(final String authzid, final String authcid, final String password,
            final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        client.verifyPlain(authzid, authcid, password, authenticationResultHandler);
    }

    @Override
    public String toString() {
        return new StringBuilder(DelegatingAuthenticationService.class.getSimpleName())
                .append("[auth-server: ").append(getConfig().getHost()).append(":").append(getConfig().getPort()).append("]")
                .toString();
    }
}
