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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to handle http basic authentication.
 * <p>
 * The tenant is transferred as part of the username in the basic authentication header similar to the MQTT
 * username/password implementation.
 * <p>
 * <ul>
 * <li>If the adapter runs in single tenant mode, the tenant is set to {@link Constants#DEFAULT_TENANT}.
 * <li>If the adapter runs in multiple tenant mode, the tenant must be part of the basic auth username, which must comply to
 * the structure authId@tenantId.
 * </ul>
 */
public class VertxRestUser extends AbstractUser {

    private static final Logger LOG  = LoggerFactory.getLogger(VertxRestUser.class);
    private static final String type = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
    private String              authId;
    private String              password;
    private String              tenantId;
    private String              userFromBasicAuth;
    private JsonObject          principal;

    public final String getType() {
        return type;
    }

    public final String getAuthId() {
        return authId;
    }

    public final String getPassword() {
        return password;
    }

    public final String getTenantId() {
        return tenantId;
    }

    public static final VertxRestUser create(final String userName, final String password,
            final boolean singleTenant) {
        VertxRestUser credentials = fillAuthIdAndTenantId(userName, singleTenant);
        if (credentials != null) {
            credentials.password = password;
        }
        return credentials;
    }

    private static VertxRestUser fillAuthIdAndTenantId(final String userFromBasicAuth, final boolean singleTenant) {
        if (userFromBasicAuth == null) {
            LOG.trace("auth object in endpoint found, but username must not be null");
            return null;
        }
        VertxRestUser credentials = new VertxRestUser();
        credentials.userFromBasicAuth = userFromBasicAuth;
        if (singleTenant) {
            credentials.authId = userFromBasicAuth;
            credentials.tenantId = Constants.DEFAULT_TENANT;
        } else {
            // multi tenantId -> <userId>@<tenantId>
            String[] userComponents = userFromBasicAuth.split("@");
            if (userComponents.length != 2) {
                LOG.trace(
                        "User {} in Basic Auth does not comply with the defined structure, must fulfil the pattern '<authId>@<tenantId>'",
                        userFromBasicAuth);
                return null;
            } else {
                credentials.authId = userComponents[0];
                credentials.tenantId = userComponents[1];
            }
        }
        return credentials;
    }

    @Override
    protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {
        LOG.debug("No authorization supported at the moment");
        handler.handle(Future.succeededFuture());
    }

    @Override
    public JsonObject principal() {
        if (principal == null) {
            principal = new JsonObject().put("username", userFromBasicAuth);
        }
        return principal;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {
    }
}
