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
package org.eclipse.hono.authentication.impl;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.AuthenticationConstants;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * An authenticator that accepts all credentials.
 */
@Component
@Profile("!sql-auth")
public final class AcceptAllAuthenticationService extends AbstractHonoAuthenticationService<ServiceConfigProperties> {

    @Override
    public void verifyExternal(String authzid, String subjectDn, Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
        handleSuccess(authzid != null && !authzid.isEmpty() ? authzid : AuthenticationConstants.getCommonName(subjectDn), authenticationResultHandler);
    }

    @Override
    public void verifyPlain(String authzid, String authcid, String password, Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
        handleSuccess(authzid != null && !authzid.isEmpty() ? authzid : authcid, authenticationResultHandler);
    }

    private void handleSuccess(final String grantedAuthorizationId, Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

        final String token = getTokenFactory().createToken(grantedAuthorizationId, new AuthoritiesImpl());
        authenticationResultHandler.handle(Future.succeededFuture(new HonoUser() {

            @Override
            public String getName() {
                return grantedAuthorizationId;
            }

            @Override
            public Authorities getAuthorities() {
                return null;
            }

            @Override
            public String getToken() {
                return token;
            }

            @Override
            public boolean isExpired() {
                return false;
            }
        }));
    }
}
