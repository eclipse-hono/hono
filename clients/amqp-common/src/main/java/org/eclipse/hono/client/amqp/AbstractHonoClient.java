/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A base class for implementing Hono API clients.
 * <p>
 * Holds a sender and a receiver to an AMQP 1.0 server and provides
 * support for closing these links gracefully.
 */
public abstract class AbstractHonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHonoClient.class);

    /**
     * The connection to the server.
     */
    protected final HonoConnection connection;

    /**
     * The vertx-proton object used for sending messages to the server.
     */
    protected ProtonSender sender;
    /**
     * The vertx-proton object used for receiving messages from the server.
     */
    protected ProtonReceiver receiver;
    /**
     * The capabilities offered by the peer.
     */
    protected List<Symbol> offeredCapabilities = Collections.emptyList();

    /**
     * Creates a client for a connection.
     *
     * @param connection The connection to use.
     * @throws NullPointerException if connection is {@code null}.
     */
    protected AbstractHonoClient(final HonoConnection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs an exception.
     * <p>
     * This method does <em>not</em> finish the span.
     *
     * @param span The span to mark.
     * @param error The exception that has occurred. If the exception is a
     *              {@link ServiceInvocationException} then a {@link Tags#HTTP_STATUS}
     *              tag is added containing the exception's error code property value.
     * @throws NullPointerException if error is {@code null}.
     */
    protected final void logError(final Span span, final Throwable error) {
        if (span != null) {
            if (ServiceInvocationException.class.isInstance(error)) {
                final ServiceInvocationException e = (ServiceInvocationException) error;
                Tags.HTTP_STATUS.set(span, e.getErrorCode());
            }
            TracingHelper.logError(span, error);
        }
    }

    /**
     * Checks if this client supports a certain capability.
     * <p>
     * The result of this method should only be considered reliable
     * if this client is open.
     *
     * @param capability The capability to check support for.
     * @return {@code true} if the capability is included in the list of
     *         capabilities that the peer has offered during link
     *         establishment, {@code false} otherwise.
     */
    public final boolean supportsCapability(final Symbol capability) {
        if (capability == null) {
            return false;
        } else {
            return offeredCapabilities.contains(capability);
        }
    }

    /**
     * Closes this client's sender and receiver links to Hono.
     * Link resources will be freed after the links are closed.
     *
     * @return A succeeded future indicating that the links have been closed.
     */
    protected final Future<Void> closeLinks() {

        final Promise<Void> result = Promise.promise();
        final Handler<Void> closeReceiver = s -> {
            if (receiver != null) {
                LOG.debug("locally closing receiver link [{}]", receiver.getSource().getAddress());
            }
            connection.closeAndFree(receiver, receiverClosed -> result.complete());
        };

        if (sender != null) {
            LOG.debug("locally closing sender link [{}]", sender.getTarget().getAddress());
            connection.closeAndFree(sender, senderClosed -> closeReceiver.handle(null));
        } else if (receiver != null) {
            closeReceiver.handle(null);
        } else {
            result.complete();
        }
        return result.future();
    }

    /**
     * Set the application properties for a Proton Message but do a check for all properties first if they only contain
     * values that the AMQP 1.0 spec allows.
     *
     * @param msg The Proton message. Must not be null.
     * @param properties The map containing application properties.
     * @throws NullPointerException if the message passed in is {@code null}.
     * @throws IllegalArgumentException if the properties contain any value that AMQP 1.0 disallows.
     */
    protected static final void setApplicationProperties(final Message msg, final Map<String, ?> properties) {
        Objects.requireNonNull(msg);

        if (properties != null) {
            final Map<String, Object> propsToAdd = new HashMap<>();
            // check the three types not allowed by AMQP 1.0 spec for application properties (list, map and array)
            for (final Map.Entry<String, ?> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    if (entry.getValue() instanceof List) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be a List", entry.getKey()));
                    } else if (entry.getValue() instanceof Map) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be a Map", entry.getKey()));
                    } else if (entry.getValue().getClass().isArray()) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be an Array", entry.getKey()));
                    }
                }
                propsToAdd.put(entry.getKey(), entry.getValue());
            }
            msg.setApplicationProperties(new ApplicationProperties(propsToAdd));
        }
    }
}
