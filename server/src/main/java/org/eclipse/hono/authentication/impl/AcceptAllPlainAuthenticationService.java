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
package org.eclipse.hono.authentication.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A PLAIN SASL authenticator that accepts all credentials.
 */
@Component
@Profile("!sql-auth")
public final class AcceptAllPlainAuthenticationService extends AbstractPlainAuthenticationService {

    @Override
    protected void verify(String authzid, String authcid, String password,
            Handler<AsyncResult<String>> authenticationResultHandler) {

        authenticationResultHandler.handle(Future.succeededFuture(authzid.length() > 0 ? authzid : authcid));
    }
}
