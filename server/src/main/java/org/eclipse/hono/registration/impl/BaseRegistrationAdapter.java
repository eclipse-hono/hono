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
package org.eclipse.hono.registration.impl;

import static org.eclipse.hono.registration.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;

import org.eclipse.hono.registration.RegistrationAdapter;
import org.eclipse.hono.registration.RegistrationConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@code RegistrationAdapter}s.
 */
public abstract class BaseRegistrationAdapter extends AbstractVerticle implements RegistrationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseRegistrationAdapter.class);
    private MessageConsumer<JsonObject> registrationConsumer;

    /**
     * Registers a Vert.x event consumer for address {@link RegistrationConstants#EVENT_BUS_ADDRESS_REGISTRATION_IN}
     * and then invokes {@link #doStart(Future)}.
     *
     * @param startFuture future to invoke once start up is complete.
     */
    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        registerConsumer();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     *
     * @param startFuture future to invoke once start up is complete.
     * @throws Exception if start-up fails
     */
    protected void doStart(final Future<Void> startFuture) throws Exception {
        // should be overridden by subclasses
        startFuture.complete();
    }

    private void registerConsumer() {
        registrationConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_REGISTRATION_IN);
        registrationConsumer.handler(this::processRegistrationMessage);
        LOG.info("listening on event bus [address: {}] for incoming registration messages",
                EVENT_BUS_ADDRESS_REGISTRATION_IN);
    }

    /**
     * Unregisters the registration message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        registrationConsumer.unregister();
        LOG.info("unregistered registration data consumer from event bus");
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this verticle.
     * <p>
     * This method is invoked by {@link #stop()} as part of the verticle deployment process.
     * </p>
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    protected final void reply(final Message<JsonObject> request, final int status) {
        final JsonObject body = request.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);

        request.reply(RegistrationConstants.getReply(status, tenantId, deviceId));
    }
}
