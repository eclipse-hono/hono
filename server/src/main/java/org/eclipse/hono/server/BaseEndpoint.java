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

    protected final boolean      singleTenant;
    protected final Vertx        vertx;
    protected final int          instanceNo;
    protected int                authServiceInstanceCount = Runtime.getRuntime().availableProcessors();
    private static final Logger  LOGGER = LoggerFactory.getLogger(BaseEndpoint.class);
    private final String         authServiceAddress;

    /**
     * 
     * @param vertx the Vertx instance to use for accessing the event bus.
     */
    protected BaseEndpoint(final Vertx vertx)
    {
        this(vertx, false, 0);
    }

    protected BaseEndpoint(final Vertx vertx, final boolean singleTenant, final int instanceNo)
    {
        this.vertx = Objects.requireNonNull(vertx);
        this.singleTenant = singleTenant;
        this.instanceNo = instanceNo;
        this.authServiceAddress = getAddressForInstanceNo(EVENT_BUS_ADDRESS_AUTHORIZATION_IN);
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     * @return {@code true} if startup succeeded.
     */
    @Override
    public boolean start() {
        return true;
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     * @return {@code true} if shutdown succeeded.
     */
    @Override
    public boolean stop() {
        return true;
    }

    /**
     * Appends this endpoint's instance number to a given base address.
     * 
     * @param baseAddress the base address.
     * @return the base address appended with a period and the instance number.
     */
    protected final String getAddressForInstanceNo(final String baseAddress) {
        return String.format("%s.%d", baseAddress, instanceNo);
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
        LOGGER.info("Endpoint [{}] does not support data retrieval, closing link.", getName());
        sender.close();
    }

    protected final ResourceIdentifier getResourceIdentifier(final String address) {
        if (isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    protected final void checkPermission(final ResourceIdentifier resource, final Handler<Boolean> permissionCheckHandler)
    {
        final JsonObject authMsg = new JsonObject();
        // TODO how to obtain subject information?
        authMsg.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        authMsg.put(RESOURCE_FIELD, resource.toString());
        authMsg.put(PERMISSION_FIELD, Permission.WRITE.toString());

        LOGGER.trace("sending auth message to authorization service [address: {}]", authServiceAddress);
        vertx.eventBus().send(authServiceAddress, authMsg,
           res -> permissionCheckHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }
}
