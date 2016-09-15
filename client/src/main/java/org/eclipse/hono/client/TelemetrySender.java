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

package org.eclipse.hono.client;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A client for uploading telemetry data to a Hono server.
 *
 */
public interface TelemetrySender {

    /**
     * Sends an AMQP 1.0 message to the telemetry endpoint configured for this client.
     * 
     * @param rawMessage the message to send.
     * @return {@code false} if the AMQP link this client uses does not have any credit left
     *        and thus cannot send the message to the Hono server's telemetry endpoint. The
     *        message will simply be discarded.
     */
    boolean send(Message rawMessage);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     * <p>
     * This parameter will be used as the value for the message's application property <em>device_id</em>.
     * </p>
     * @param payload The data to send.
     * <p>
     * The payload's UTF-8 byte representation will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * </p>
     * @param contentType The content type of the payload.
     * <p>
     * This parameter will be used as the value for the message's <em>content-type</em> property.
     * </p>
     * @return {@code false} if the AMQP link this client uses does not have any credit left
     *        and thus cannot send the data to the Hono server's telemetry endpoint. The data will
     *        simply be discarded.
     */
    boolean send(String deviceId, String payload, String contentType);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     * <p>
     * This parameter will be used as the value for the message's application property <em>device_id</em>.
     * </p>
     * @param payload The data to send.
     * <p>
     * The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * </p>
     * @param contentType The content type of the payload.
     * <p>
     * This parameter will be used as the value for the message's <em>content-type</em> property.
     * </p>
     * @return {@code false} if the AMQP link this client uses does not have any credit left
     *        and thus cannot send the data to the Hono server's telemetry endpoint. The data will
     *        simply be discarded.
     */
    boolean send(String deviceId, byte[] payload, String contentType);

    /**
     * Sets a callback for handling errors that occur while interacting with the server.
     * <p>
     * When this handler is called back it will most likely mean that this client's link to the
     * Hono server's telemetry endpoint has been closed. This may occur if a message sent
     * does not comply with the Hono Telemetry API or if the client is not authorized to upload
     * data for the tenant this client has been configured for.
     * 
     * The former problem usually indicates an implementation error in the client whereas the
     * latter problem indicates that the client's authorizations have been changed on the server
     * side after it has connected to the server.
     * </p>
     * 
     * @param errorHandler The callback for handling errors.
     */
    void setErrorHandler(Handler<AsyncResult<Void>> errorHandler);

    /**
     * Closes the AMQP link(s) with the Hono server this client is configured to use.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * </p>
     * 
     * @param closeHandler A handler that is called back with the result of the attempt to close the links.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);
}
