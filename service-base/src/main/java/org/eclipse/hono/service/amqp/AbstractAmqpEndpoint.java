/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service.amqp;

import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.service.AbstractEndpoint;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Base class for AMQP based Hono endpoints.
 *
 * @param <T> The type of configuration properties this endpoint understands.
 */
public abstract class AbstractAmqpEndpoint<T> extends AbstractEndpoint implements AmqpEndpoint {

    /**
     * The configuration properties for this endpoint.
     */
    protected T config;

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected AbstractAmqpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Sets configuration properties.
     *
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    public final void setConfiguration(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Closes the link to a proton based receiver client.
     *
     * @param client The client to detach.
     */
    protected void onLinkDetach(final ProtonReceiver client) {
        onLinkDetach(client, (ErrorCondition) null);
    }

    /**
     * Closes a link to a proton based client.
     *
     * @param client The client to detach.
     * @param error The error condition to convey to the client when closing the link.
     */
    protected final void onLinkDetach(final ProtonLink<?> client, final ErrorCondition error) {
        if (error == null) {
            logger.debug("closing link [{}]", client.getName());
        } else {
            logger.debug("closing link [{}]: {}", client.getName(), error.getDescription());
            client.setCondition(error);
        }
        client.close();
    }

    @Override
    public void onLinkAttach(final ProtonConnection con, final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data upload, closing link.", getName());
        receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be written to"));
        receiver.close();
    }

    @Override
    public void onLinkAttach(final ProtonConnection con, final ProtonSender sender, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data retrieval, closing link.", getName());
        sender.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be read from"));
        sender.close();
    }
}
