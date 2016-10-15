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

import java.util.Map;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A client for uploading telemetry data to a Hono server.
 *
 */
public interface TelemetrySender {

    /**
     * Checks if this sender can send or buffer (and send later) a telemetry message.
     * 
     * @return {@code false} if a message can be sent or buffered.
     */
    boolean sendQueueFull();

    /**
     * Sets a handler to be notified once this sender has capacity available to send or
     * buffer a telemetry message.
     * <p>
     * The handler registered using this method will be invoked <em>exactly once</em> when
     * this sender is replenished with more credit from the server. For subsequent notifications
     * to be received, a new handler must be registered.
     * <p>
     * Client code should register a handler after it has checked this sender's capacity to send
     * messages using the <em>sendQueueFull</em> method, e.g.
     * <pre>
     * TelemetrySender sender;
     * ...
     * sender.send(msg);
     * if (sender.sendQueueFull()) {
     *     sender.sendQueueDrainHandler(replenished -&gt; {
     *         // send more messages
     *     });
     * }
     * </pre>
     * 
     * @param handler The handler to invoke when this sender has been replenished with credit.
     */
    void sendQueueDrainHandler(Handler<Void> handler);

    /**
     * Sends an AMQP 1.0 message to the telemetry endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     * 
     * @param message The message to send.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if the message is {@code null}.
     */
    void send(Message message, Handler<Void> capacityAvailableHandler);

    /**
     * Sends an AMQP 1.0 message to the telemetry endpoint configured for this client.
     * 
     * @param message The message to send.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if the message is {@code null}.
     */
    boolean send(Message message);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, String payload, String contentType);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     * 
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    void send(String deviceId, String payload, String contentType, Handler<Void> capacityAvailableHandler);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    boolean send(String deviceId, byte[] payload, String contentType);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     * 
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     */
    void send(String deviceId, byte[] payload, String contentType, Handler<Void> capacityAvailableHandler);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, Map<String, ?> properties, String payload, String contentType);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     */
    boolean send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    void send(String deviceId, Map<String, ?> properties, String payload, String contentType, Handler<Void> capacityAvailableHandler);

    /**
     * Uploads telemetry data for a given device to the telemetry endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     */
    void send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType, Handler<Void> capacityAvailableHandler);

    /**
     * Sets a callback for handling the closing of this sender due to an error condition indicated by
     * the server.
     * <p>
     * When this handler is called back, this client's link to the Hono server's telemetry endpoint has been
     * closed due to an error condition. Possible reasons include:
     * <ul>
     * <li>A sent message does not comply with the Hono Telemetry API.</li>
     * <li>This client is not authorized to upload data for the tenant it has been configured for.</li>
     * </ul>
     * The former problem usually indicates an implementation error in the client whereas the
     * latter problem indicates that the client's authorizations might have changed on the server
     * side after it has connected to the server.
     * 
     * @param errorHandler The callback for handling the error condition. The error handler's <em>cause</em>
     *                     property will contain the cause for the closing of the link.
     */
    void setErrorHandler(Handler<AsyncResult<Void>> errorHandler);

    /**
     * Closes the AMQP link with the Hono server this sender is using.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * 
     * @param closeHandler A handler that is called back with the outcome of the attempt to close the link.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);
}
