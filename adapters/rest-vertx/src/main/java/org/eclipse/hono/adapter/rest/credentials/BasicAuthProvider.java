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

package org.eclipse.hono.adapter.rest.credentials;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.eclipse.hono.adapter.rest.VertxBasedRestProtocolAdapter;

/**
 * Helper class implementing {@link AuthProvider}. The Vertx components handle the header extraction and return the
 * appropriate error codes if the user authorization fails.
 */
public class BasicAuthProvider implements AuthProvider {

    private VertxBasedRestProtocolAdapter adapter;

    public BasicAuthProvider(VertxBasedRestProtocolAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        String username = authInfo.getString("username");
        String password = authInfo.getString("password");
        VertxRestUser user = VertxRestUser.create(username, password, adapter.getConfig().isSingleTenant());
        if (user == null) {
            resultHandler.handle(Future.failedFuture("login failed"));
        } else {
            adapter.validateCredentialsForDevice(user.getTenantId(), user.getType(), user.getAuthId(),
                    user.getPassword()).setHandler(attempt -> {
                        if (attempt.succeeded()) {
                            resultHandler.handle(Future.succeededFuture(user));
                        } else {
                            resultHandler.handle(Future.failedFuture("login failed"));
                        }
                    });
        }
    }

    public static BasicAuthProvider create(VertxBasedRestProtocolAdapter adapter) {
        return new BasicAuthProvider(adapter);
    }
}
