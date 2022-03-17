/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.connection;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.amqp.tracing.MessageAnnotationsExtractAdapter;
import org.eclipse.hono.client.amqp.tracing.MessageAnnotationsInjectAdapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
import io.opentracing.propagation.Format;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;

/**
 * AMQP 1.0 related helper methods.
 *
 */
public final class AmqpUtils {

    /**
     * Indicates that an AMQP request cannot be processed due to a perceived client error.
     */
    public static final Symbol AMQP_BAD_REQUEST = Symbol.valueOf("hono:bad-request");
    /**
     * Indicates that an AMQP connection is closed due to inactivity.
     */
    public static final Symbol AMQP_ERROR_INACTIVITY = Symbol.valueOf("hono:inactivity");

    /**
     * The AMQP capability indicating support for routing messages as defined by
     * <a href="http://docs.oasis-open.org/amqp/anonterm/v1.0/anonterm-v1.0.html">
     * Anonymous Terminus for Message Routing</a>.
     */
    public static final Symbol CAP_ANONYMOUS_RELAY = Symbol.valueOf("ANONYMOUS-RELAY");

    /**
     * The key that an authenticated client's principal is stored under in a {@code ProtonConnection}'s
     * attachments.
     */
    public static final String KEY_CLIENT_PRINCIPAL = "CLIENT_PRINCIPAL";

    /**
     * The subject name to use for anonymous clients.
     */
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    /**
     * The principal to use for anonymous clients.
     */
    public static final HonoUser PRINCIPAL_ANONYMOUS = new HonoUserAdapter() {

        private final Authorities authorities = new Authorities() {

            @Override
            public Map<String, Object> asMap() {
                return Collections.emptyMap();
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final Activity intent) {
                return false;
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final String operation) {
                return false;
            }
        };

        @Override
        public String getName() {
            return SUBJECT_ANONYMOUS;
        }

        @Override
        public Authorities getAuthorities() {
            return authorities;
        }
    };

    private static final String AMQP_ANNOTATION_NAME_TRACE_CONTEXT = "x-opt-trace-context";

    private AmqpUtils() {
        // prevent instantiation
    }


    /**
     * Gets the principal representing an authenticated peer.
     *
     * @param record The attachments to retrieve the principal from.
     * @return The principal representing the authenticated client or {@link Constants#PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated or record is {@code null}.
     */
    private static HonoUser getClientPrincipal(final Record record) {

        if (record != null) {
            final HonoUser client = record.get(KEY_CLIENT_PRINCIPAL, HonoUser.class);
            return client != null ? client : PRINCIPAL_ANONYMOUS;
        } else {
            return PRINCIPAL_ANONYMOUS;
        }
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @return The principal representing the authenticated client or {@link #PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated.
     * @throws NullPointerException if the connection is {@code null}.
     */
    public static HonoUser getClientPrincipal(final ProtonConnection con) {
        final Record attachments = Objects.requireNonNull(con).attachments();
        return getClientPrincipal(attachments);
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @param principal The principal representing the authenticated client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void setClientPrincipal(final ProtonConnection con, final HonoUser principal) {
        Objects.requireNonNull(principal);
        final Record attachments = Objects.requireNonNull(con).attachments();
        attachments.set(KEY_CLIENT_PRINCIPAL, HonoUser.class, principal);
    }

    /**
     * Injects a {@code SpanContext} into an AMQP {@code Message}.
     * <p>
     * The span context will be written to the message annotations of the given message.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @param message The AMQP {@code Message} object to inject the context into.
     * @throws NullPointerException if tracer or message is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final SpanContext spanContext, final Message message) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(message);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP,
                    new MessageAnnotationsInjectAdapter(message, AMQP_ANNOTATION_NAME_TRACE_CONTEXT));
        }
    }

    /**
     * Extracts a {@code SpanContext} from an AMQP {@code Message}.
     * <p>
     * The span context will be read from the message annotations of the given message.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param message The AMQP {@code Message} to extract the context from.
     * @return The context or {@code null} if the given {@code Message} does not contain a context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer, final Message message) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(message);

        return tracer.extract(Format.Builtin.TEXT_MAP,
                new MessageAnnotationsExtractAdapter(message, AMQP_ANNOTATION_NAME_TRACE_CONTEXT));
    }

    /**
     * Rejects and settles an AMQP 1.0 message.
     *
     * @param delivery The message's delivery handle.
     * @param error The error condition to set as the reason for rejecting the message (may be {@code null}.
     * @throws NullPointerException if delivery is {@code null}.
     */
    public static void rejected(final ProtonDelivery delivery, final ErrorCondition error) {

        Objects.requireNonNull(delivery);

        final Rejected rejected = new Rejected();
        rejected.setError(error); // doesn't matter if null
        delivery.disposition(rejected, true);
    }

    /**
     * Gets the value of one of a message's <em>application properties</em>.
     *
     * @param <T> The expected type of the property to retrieve the value of.
     * @param message The message containing the application properties to retrieve the value from.
     * @param name The property name.
     * @param type The expected value type.
     * @return The value or {@code null} if the message's application properties do not contain a value of the
     *         expected type for the given name.
     */
    public static <T> T getApplicationProperty(
            final Message message,
            final String name,
            final Class<T> type) {
        return Optional.ofNullable(message)
                .flatMap(msg -> Optional.ofNullable(msg.getApplicationProperties()))
                .map(ApplicationProperties::getValue)
                .map(props -> props.get(name))
                .filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }
}
