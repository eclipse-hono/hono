/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.vertx.mqtt.messages.impl.MqttSubscribeMessageImpl;
import io.vertx.mqtt.messages.impl.MqttUnsubscribeMessageImpl;

/**
 * Support for mocking MQTT connections to a {@link AbstractMqttToAmqpProtocolGateway}.
 **/
public final class ProtocolGatewayTestHelper {

    private ProtocolGatewayTestHelper() {
    }

    /**
     * Creates a mocked MQTT endpoint and connects it to the given protocol gateway by calling the endpoint handler.
     * Authenticates with the given username and password.
     *
     * @param gateway The protocol gateway to connect to.
     * @param username The username.
     * @param password The password.
     * @return The connected and authenticated endpoint mock.
     */
    public static MqttEndpoint connectMqttEndpoint(final AbstractMqttToAmqpProtocolGateway gateway,
            final String username,
            final String password) {

        final MqttEndpoint endpoint = createMqttEndpoint();

        when(endpoint.auth()).thenReturn(new MqttAuth(username, password));

        gateway.handleEndpointConnection(endpoint);
        return endpoint;
    }

    /**
     * Creates a mocked MQTT endpoint and connects it to the given protocol gateway by calling the endpoint handler.
     * Authenticates with the given client certificate.
     *
     * @param gateway The protocol gateway to connect to.
     * @param deviceCertificate The X.509 client certificate.
     * @return The connected and authenticated endpoint mock.
     */
    public static MqttEndpoint connectMqttEndpointWithClientCertificate(final AbstractMqttToAmqpProtocolGateway gateway,
            final X509Certificate deviceCertificate) {

        final MqttEndpoint endpoint = createMqttEndpoint();

        when(endpoint.isSsl()).thenReturn(true);

        final SSLSession sslSession = mock(SSLSession.class);
        try {
            when(sslSession.getPeerCertificates()).thenReturn(new Certificate[] { deviceCertificate });
        } catch (SSLPeerUnverifiedException e) {
            throw new RuntimeException("this should not be possible", e);
        }
        when(endpoint.sslSession()).thenReturn(sslSession);

        gateway.handleEndpointConnection(endpoint);
        return endpoint;
    }

    /**
     * Simulates sending a MQTT subscribe message by invoking the subscribe handler, that has been set on the given mock
     * endpoint during the connection establishment in one of the "connect..." methods in this class.
     * <p>
     * The given topics are all subscribed with QoS "AT_LEAST_ONCE".
     *
     * @param endpoint The connected endpoint mock.
     * @param topicFilters The topic filters to subscribe for.
     * @return A random message id.
     *
     * @see #connectMqttEndpoint(AbstractMqttToAmqpProtocolGateway, String, String)
     * @see #connectMqttEndpointWithClientCertificate(AbstractMqttToAmqpProtocolGateway, X509Certificate)
     */
    public static int subscribe(final MqttEndpoint endpoint, final String... topicFilters) {

        final MqttTopicSubscription[] mqttTopicSubscriptions = Arrays.stream(topicFilters)
                .map(topic -> new MqttTopicSubscription(topic, MqttQoS.AT_LEAST_ONCE))
                .toArray(MqttTopicSubscription[]::new);

        return subscribe(endpoint, mqttTopicSubscriptions);
    }

    /**
     * Simulates sending a MQTT subscribe message by invoking the subscribe handler, that has been set on the given mock
     * endpoint during the connection establishment in one of the "connect..." methods in this class.
     *
     * @param endpoint The connected endpoint mock.
     * @param subscriptions The topic subscriptions to subscribe for.
     * @return A random message id.
     *
     * @see #connectMqttEndpoint(AbstractMqttToAmqpProtocolGateway, String, String)
     * @see #connectMqttEndpointWithClientCertificate(AbstractMqttToAmqpProtocolGateway, X509Certificate)
     */
    public static int subscribe(final MqttEndpoint endpoint, final MqttTopicSubscription... subscriptions) {

        final ArgumentCaptor<Handler<MqttSubscribeMessage>> captor = argumentCaptorHandler();
        verify(endpoint).subscribeHandler(captor.capture());

        final int messageId = newRandomMessageId();
        captor.getValue().handle(new MqttSubscribeMessageImpl(messageId, Arrays.asList(subscriptions)));

        return messageId;
    }

    /**
     * Simulates sending a MQTT unsubscribe message by invoking the unsubscribe handler, that has been set on the given
     * mock endpoint during the connection establishment in one of the "connect..." methods in this class.
     *
     * @param endpoint The connected endpoint mock.
     * @param topics The topic filters to unsubscribe.
     * @return A random message id.
     *
     * @see #connectMqttEndpoint(AbstractMqttToAmqpProtocolGateway, String, String)
     * @see #connectMqttEndpointWithClientCertificate(AbstractMqttToAmqpProtocolGateway, X509Certificate)
     */
    public static int unsubscribe(final MqttEndpoint endpoint, final String... topics) {

        final ArgumentCaptor<Handler<MqttUnsubscribeMessage>> captor = argumentCaptorHandler();
        verify(endpoint).unsubscribeHandler(captor.capture());

        final int messageId = newRandomMessageId();
        captor.getValue().handle(new MqttUnsubscribeMessageImpl(messageId, Arrays.asList(topics)));

        return messageId;
    }

    /**
     * Simulates sending a MQTT publish message by invoking the publish handler, that has been set on the given mock
     * endpoint during the connection establishment in one of the "connect..." methods in this class.
     *
     * @param endpoint The connected endpoint mock.
     * @param payload The payload of the message.
     * @param topic The topic of the message.
     *
     * @see #connectMqttEndpoint(AbstractMqttToAmqpProtocolGateway, String, String)
     * @see #connectMqttEndpointWithClientCertificate(AbstractMqttToAmqpProtocolGateway, X509Certificate)
     */
    public static void sendMessage(final MqttEndpoint endpoint, final Buffer payload, final String topic) {

        final ArgumentCaptor<Handler<MqttPublishMessage>> captor = argumentCaptorHandler();
        verify(endpoint).publishHandler(captor.capture());

        final MqttPublishMessage mqttMessage = mock(MqttPublishMessage.class);
        when(mqttMessage.payload()).thenReturn(payload);
        when(mqttMessage.topicName()).thenReturn(topic);

        captor.getValue().handle(mqttMessage);
    }

    /**
     * Returns a self signed certificate.
     *
     * @return A new X.509 certificate.
     */
    public static X509Certificate createCertificate() {
        final SelfSignedCertificate selfSignedCert = SelfSignedCertificate.create("eclipse.org");
        try {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new FileInputStream(selfSignedCert.certificatePath()));
        } catch (CertificateException | FileNotFoundException e) {
            throw new RuntimeException("Generating self signed cert failed", e);
        }
    }

    /**
     * Matches any handler of given type, excluding nulls.
     *
     * @param <T> The handler type.
     * @return The value returned by {@link ArgumentMatchers#any(Class)}.
     */
    public static <T> Handler<T> anyHandler() {
        @SuppressWarnings("unchecked")
        final Handler<T> result = ArgumentMatchers.any(Handler.class);
        return result;
    }

    /**
     * Argument captor for a handler.
     *
     * @param <T> The handler type.
     * @return The value returned by {@link ArgumentCaptor#forClass(Class)}.
     */
    public static <T> ArgumentCaptor<Handler<T>> argumentCaptorHandler() {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<T>> result = ArgumentCaptor.forClass(Handler.class);
        return result;
    }

    private static MqttEndpoint createMqttEndpoint() {

        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(true);
        when(endpoint.clientIdentifier()).thenReturn("the-client-id");
        addCloseHandlerToEndpoint(endpoint);

        return endpoint;
    }

    /**
     * When the endpoint is closed, the close handler is invoked.
     */
    private static void addCloseHandlerToEndpoint(final MqttEndpoint endpoint) {
        when(endpoint.closeHandler(anyHandler())).then(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            doAnswer(s -> {
                handler.handle(null);
                return null;
            }).when(endpoint).close();
            return null;
        });
    }

    private static int newRandomMessageId() {
        return ThreadLocalRandom.current().nextInt();
    }

}
