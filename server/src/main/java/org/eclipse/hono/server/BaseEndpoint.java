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
package org.eclipse.hono.server;

import static org.eclipse.hono.authorization.AuthorizationConstants.AUTH_SUBJECT_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
import static org.eclipse.hono.authorization.AuthorizationConstants.PERMISSION_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.RESOURCE_FIELD;

import java.util.Objects;

import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonSender;

/**
 * Base class for Hono endpoints.
 */
public abstract class BaseEndpoint implements Endpoint{

    private static final       Logger LOGGER = LoggerFactory.getLogger(BaseEndpoint.class);

    protected final boolean    singleTenant;
    protected final Vertx      vertx;
    protected final int        id;

    /**
     * 
     * @param vertx the Vertx instance to use for accessing the event bus.
     */
    protected BaseEndpoint(final Vertx vertx)
    {
        this(vertx, false, 0);
    }

    protected BaseEndpoint(final Vertx vertx, final boolean singleTenant, final int instanceId)
    {
        this.vertx = Objects.requireNonNull(vertx);
        this.singleTenant = singleTenant;
        this.id = instanceId;
    }

    protected final String getAddressWithId(final String baseAddress) {
        StringBuilder b = new StringBuilder(baseAddress);
        if (id > 0) {
            b.append(".").append(id);
        }
        return b.toString();
    }

    /**
     * Checks if Hono runs in single-tenant mode.
     * <p>
     * In single-tenant mode Hono will accept target addresses in {@code ATTACH} messages
     * that do not contain a tenant ID and will assume {@link Constants#DEFAULT_TENANT} instead.
     * </p>
     * <p>
     * The default value of this property is {@code false}.
     * </p>
     *
     * @return {@code true} if Hono runs in single-tenant mode.
     */
    public final boolean isSingleTenant() {
        return singleTenant;
    }

    @Override
    public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        LOGGER.info("Endpoint {} is not capable to send messages, closing link.", getName());
        sender.close();
    }

    protected final ResourceIdentifier getResourceIdentifier(final String address) {
        if (isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    protected final void checkPermission(final ResourceIdentifier messageAddress, final Handler<Boolean> permissionCheckHandler)
    {
        final JsonObject authMsg = new JsonObject();
        // TODO how to obtain subject information?
        authMsg.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        authMsg.put(RESOURCE_FIELD, messageAddress.toString());
        authMsg.put(PERMISSION_FIELD, Permission.WRITE.toString());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, authMsg,
           res -> permissionCheckHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }
}
