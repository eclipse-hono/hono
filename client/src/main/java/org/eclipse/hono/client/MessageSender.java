/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import org.apache.qpid.proton.message.Message;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for publishing messages to Hono.
 *
 */
public interface MessageSender extends CreditBasedSender {

    /**
     * Gets the name of the endpoint this sender sends messages to.
     * <p>
     * The name returned is implementation specific, e.g. an implementation
     * that can be used to upload telemetry data to Hono will return
     * the value <em>telemetry</em>.
     *
     * @return The endpoint name.
     */
    String getEndpoint();

    /**
     * Closes the AMQP link with the Hono server this sender is using.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * 
     * @param closeHandler A handler that is called back with the outcome of the attempt to close the link.
     * @throws NullPointerException if the handler is {@code null}.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);

    /**
     * Checks if this sender is (locally) open.
     * <p>
     * Note that the value returned is valid during execution of the current vert.x handler only.
     * 
     * @return {@code true} if this sender can be used to send messages to the peer.
     */
    boolean isOpen();

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery will be locally settled only, if the implementing class
     *         uses <em>at most once</em> delivery semantics. Otherwise, the delivery
     *         will be settled locally and remotely (<em>at least once</em> semantics).
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit. It will be failed with either a
     *         {@code ServerErrorException} or a {@link ClientErrorException}
     *         if the message could not be processed and the implementing class uses
     *         <em>at least once</em> delivery semantics.
     * @throws NullPointerException if the message is {@code null}.
     */
    Future<ProtonDelivery> send(Message message);

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     * <p>
     * This default implementation simply returns the result of {@link #send(Message)}.
     * 
     * @param message The message to send.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if message is {@code null}.
     */
    default Future<ProtonDelivery> send(Message message, SpanContext context) {
        return send(message);
    }

    /**
     * Sends an AMQP 1.0 message to the peer and waits for the disposition indicating
     * the outcome of the transfer.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been accepted (and settled)
     *         by the peer.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if the message is {@code null}.
     */
    Future<ProtonDelivery> sendAndWaitForOutcome(Message message);

    /**
     * Sends an AMQP 1.0 message to the peer and waits for the disposition indicating
     * the outcome of the transfer.
     * <p>
     * This default implementation simply returns the result of {@link #sendAndWaitForOutcome(Message)}.
     * 
     * @param message The message to send.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been accepted (and settled)
     *         by the peer.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if message is {@code null}.
     */
    default Future<ProtonDelivery> sendAndWaitForOutcome(Message message, SpanContext context) {
        return sendAndWaitForOutcome(message);
    }
}
