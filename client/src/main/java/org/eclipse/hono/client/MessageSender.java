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
 *
 */

package org.eclipse.hono.client;

import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for publishing messages to a Hono server.
 *
 */
public interface MessageSender {

    /**
     * Gets the number of messages this sender can send based on its current number of credits.
     * 
     * @return The number of messages.
     */
    int getCredit();

    /**
     * Checks if this sender can send or buffer (and send later) a message.
     * 
     * @return {@code false} if a message can be sent or buffered.
     */
    boolean sendQueueFull();

    /**
     * Sets a handler to be notified once this sender has capacity available to send or
     * buffer a message.
     * <p>
     * The handler registered using this method will be invoked <em>exactly once</em> when
     * this sender is replenished with more credit from the server. For subsequent notifications
     * to be received, a new handler must be registered.
     * <p>
     * Client code should register a handler after it has checked this sender's capacity to send
     * messages using the <em>sendQueueFull</em> method, e.g.
     * <pre>
     * MessageSender sender;
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
     * @throws IllegalStateException if there already is a handler registered. Note that this means
     *                               that this sender is already waiting for credit.
     */
    void sendQueueDrainHandler(Handler<Void> handler);

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     *
     * @param message The message to send.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @throws NullPointerException if the message is {@code null}.
     */
    void send(Message message, Handler<Void> capacityAvailableHandler, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     *
     * @param message The message to send.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if the message is {@code null}.
     */
    boolean send(Message message, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
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
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     * 
     * @param message The message to send.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if the message is {@code null}.
     */
    boolean send(Message message);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, String payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, String payload, String contentType, String registrationAssertion, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    void send(String deviceId, String payload, String contentType, String registrationAssertion, Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    boolean send(String deviceId, byte[] payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    boolean send(String deviceId, byte[] payload, String contentType, String registrationAssertion, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     */
    void send(String deviceId, byte[] payload, String contentType, String registrationAssertion, Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, Map<String, ?> properties, String payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    boolean send(String deviceId, Map<String, ?> properties, String payload, String contentType, String registrationAssertion, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     */
    boolean send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param dispositionHandler The handler for disposition updates that accepts a message id and updated disposition
     * @return {@code true} if this client has enough capacity to accept and send the message. If not,
     *         the message is discarded and {@code false} is returned.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     */
    boolean send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType, String registrationAssertion, BiConsumer<Object, ProtonDelivery> dispositionHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload or content type is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    void send(String deviceId, Map<String, ?> properties, String payload, String contentType, String registrationAssertion, Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
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
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              The {@linkplain RegistrationClient#assertRegistration(String, Handler) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     */
    void send(String deviceId, Map<String, ?> properties, byte[] payload, String contentType, String registrationAssertion, Handler<Void> capacityAvailableHandler);

    /**
     * Sets a callback for handling the closing of this sender due to an error condition indicated by
     * the server.
     * <p>
     * When this handler is called back, this client's link to the Hono server's endpoint has been
     * closed due to an error condition. Possible reasons include:
     * <ul>
     * <li>A sent message does not comply with the Hono API.</li>
     * <li>This client is not authorized to publish messages for the tenant it has been configured for.</li>
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
     * Sets the default callback for disposition updates for messages sent with this {@link MessageSender}.
     *
     * @param dispositionHandler consumer that accepts a message id and updated disposition
     */
    void setDefaultDispositionHandler(BiConsumer<Object, ProtonDelivery> dispositionHandler);

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
     * 
     * @return {@code true} if this sender can be used to send messages to the peer.
     */
    boolean isOpen();
}
