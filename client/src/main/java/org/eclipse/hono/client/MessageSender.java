/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.util.Map;

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
     * Checks if this sender can send or buffer (and send later) a message.
     * <p>
     * Note that the value returned is valid during execution of the current vert.x handler only.
     * 
     * @return {@code true} if a message can not be sent or buffered at the moment.
     * @deprecated Explicitly check availability of credit using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    boolean sendQueueFull();

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
     * Checks if the peer that this sender is connected to
     * requires messages to contain a registration assertion.
     * <p>
     * The result of this method should only be considered
     * if this sender is open.
     * 
     * @return {@code true} if messages must contain an assertion,
     *         {@code false} otherwise.
     */
    boolean isRegistrationAssertionRequired();

    /**
     * Sends an AMQP 1.0 message to the endpoint configured for this client.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery will be locally settled only, if the implementing class
     *         uses <em>at most once</em> delivery semantics. Otherwise, the the delivery
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

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
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
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(String deviceId, String payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
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
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(String deviceId, byte[] payload, String contentType, String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
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
     * @throws NullPointerException if any of device id, payload, content type or registration assertion
     *                              is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            String payload,
            String contentType,
            String registrationAssertion);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
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
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            byte[] payload,
            String contentType,
            String registrationAssertion);

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
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if the message is {@code null}.
     * @deprecated Use {@link #send(Message)} instead, optionally checking availability of credit
     *             first using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    Future<ProtonDelivery> send(Message message, Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     * @deprecated Use {@link #send(String, String, String, String)} instead, optionally checking
     *             availability of credit first using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    Future<ProtonDelivery> send(
            String deviceId,
            String payload,
            String contentType,
            String registrationAssertion,
            Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     * @deprecated Use {@link #send(String, byte[], String, String)} instead, optionally checking
     *             availability of credit first using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    Future<ProtonDelivery> send(
            String deviceId,
            byte[] payload,
            String contentType,
            String registrationAssertion,
            Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload.
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     * @deprecated Use {@link #send(String, Map, String, String, String)} instead, optionally checking
     *             availability of credit first using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            String payload,
            String contentType,
            String registrationAssertion,
            Handler<Void> capacityAvailableHandler);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * <p>
     * The message will be sent immediately if this client has enough credit available on its
     * link to the Hono server or it will be sent later after this client has been replenished
     * with more credit. In both cases the handler will be notified <em>once only</em> when this
     * sender has capacity available for accepting and sending the next message.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload.
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param registrationAssertion A JSON Web Token asserting that the device is enabled and belongs to the tenant that
     *                              this sender has been created for.
     *                              <p>
     *                              The {@linkplain RegistrationClient#assertRegistration(String) registration
     *                              client} can be used to obtain such an assertion.
     * @param capacityAvailableHandler The handler to notify when this sender can accept and send
     *                                 another message.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     * @deprecated Use {@link #send(String, Map, byte[], String, String)} instead, optionally checking
     *             availability of credit first using {@link CreditBasedSender#getCredit()}.
     */
    @Deprecated
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            byte[] payload,
            String contentType,
            String registrationAssertion,
            Handler<Void> capacityAvailableHandler);
}
