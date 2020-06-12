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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.client.device.amqp.CommandResponder;
import org.eclipse.hono.client.device.amqp.EventSender;
import org.eclipse.hono.client.device.amqp.TelemetrySender;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientCommandConsumer;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientCommandResponseSender;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientEventSenderImpl;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientTelemetrySenderImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.CommandResponseMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.DownstreamMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.TelemetryMessage;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of {@link AbstractMqttProtocolGateway}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AbstractMqttProtocolGatewayTest {

    private final ClientConfigProperties amqpClientConfig = new ClientConfigProperties();
    private final Vertx vertx = mock(Vertx.class);
    private final ProtonSender protonSender = mockProtonSender();
    private final NetServer netServer = mock(NetServer.class);
    private final AmqpAdapterClientFactory amqpAdapterClientFactory = mock(AmqpAdapterClientFactory.class);
    private Consumer<Message> commandHandler;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setUp() {
        final HonoConnection connection = mockHonoConnection(vertx, amqpClientConfig);

        when(amqpAdapterClientFactory.connect()).thenReturn(Future.succeededFuture());

        final Future<EventSender> eventSender = AmqpAdapterClientEventSenderImpl
                .createWithAnonymousLinkAddress(connection, TestMqttProtocolGateway.TENANT_ID, s -> {
                });
        when(amqpAdapterClientFactory.getOrCreateEventSender()).thenReturn(eventSender);

        final Future<TelemetrySender> telemetrySender = AmqpAdapterClientTelemetrySenderImpl
                .createWithAnonymousLinkAddress(connection, TestMqttProtocolGateway.TENANT_ID, s -> {
                });
        when(amqpAdapterClientFactory.getOrCreateTelemetrySender()).thenReturn(telemetrySender);

        final Future<CommandResponder> commandResponseSender = AmqpAdapterClientCommandResponseSender
                .createWithAnonymousLinkAddress(connection, TestMqttProtocolGateway.TENANT_ID, s -> {
                });
        when(amqpAdapterClientFactory.getOrCreateCommandResponseSender()).thenReturn(commandResponseSender);

        when(amqpAdapterClientFactory.createDeviceSpecificCommandConsumer(anyString(), any()))
                .thenAnswer(invocation -> {
                    final Consumer<Message> msgHandler = invocation.getArgument(1);
                    setCommandHandler(msgHandler);
                    return AmqpAdapterClientCommandConsumer.create(connection, TestMqttProtocolGateway.TENANT_ID,
                            TestMqttProtocolGateway.DEVICE_ID,
                            (protonDelivery, message) -> msgHandler.accept(message));
                });

        when(vertx.createNetServer(any())).thenReturn(netServer);
        when(netServer.listen(anyInt(), anyString(), ProtocolGatewayTestHelper.anyHandler())).then(invocation -> {
            final Handler<AsyncResult<NetServer>> handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(netServer));
            return netServer;
        });

        doAnswer(invocation -> {
            final Promise<Void> handler = invocation.getArgument(0);
            handler.complete();
            return null;
        }).when(netServer).close(ProtocolGatewayTestHelper.anyHandler());

    }

    /**
     * Verifies that the MqttServerOptions for the MQTT server are taken from the given the server configuration.
     */
    @Test
    public void testMqttServerConfigWithoutTls() {
        final int port = 1111;
        final String bindAddress = "127.0.0.127";

        final MqttProtocolGatewayConfig config = new MqttProtocolGatewayConfig();
        config.setBindAddress(bindAddress);
        config.setPort(port);

        // GIVEN a protocol gateway with properties configured
        final TestMqttProtocolGateway gateway = createGateway(config);

        // WHEN the server options are created
        final MqttServerOptions serverOptions = gateway.getMqttServerOptions();

        // THEN the server options contain the configured properties...
        assertThat(serverOptions.getHost()).isEqualTo(bindAddress);
        assertThat(serverOptions.getPort()).isEqualTo(port);

        // ...AND TLS has not been enabled
        assertThat(serverOptions.isSsl()).isFalse();
        assertThat(serverOptions.getKeyCertOptions()).isNull();
        assertThat(serverOptions.getTrustOptions()).isNull();
        assertThat(serverOptions.getClientAuth()).isEqualTo(ClientAuth.NONE);

    }

    /**
     * Verifies that the MqttServerOptions for the MQTT server are configured correctly for the use of TLS when setting
     * the corresponding properties in the server configuration.
     */
    @Test
    public void testMqttServerConfigWithTls() {

        final String keyStorePath = "../core/target/certs/authServerKeyStore.p12";
        final List<String> enabledProtocols = Arrays.asList("TLSv1", "TLSv1.1", "TLSv1.2");

        // GIVEN a protocol gateway with TLS configured
        final MqttProtocolGatewayConfig config = new MqttProtocolGatewayConfig();
        config.setKeyStorePath(keyStorePath); // sets KeyCertOptions
        config.setSecureProtocols(enabledProtocols);
        config.setSni(true);

        final TestMqttProtocolGateway gateway = createGateway(config);

        // WHEN the server options are created
        final MqttServerOptions serverOptions = gateway.getMqttServerOptions();

        // THEN the TLS configuration is correct
        assertThat(serverOptions.isSsl()).isTrue();
        assertThat(serverOptions.getKeyCertOptions()).isEqualTo(new PfxOptions().setPath(keyStorePath));

        final LinkedHashSet<String> expectedEnabledSecureProtocols = new LinkedHashSet<>(enabledProtocols);
        assertThat(serverOptions.getEnabledSecureTransportProtocols()).isEqualTo(expectedEnabledSecureProtocols);
        assertThat(serverOptions.isSni()).isTrue();

        // and not trust options have been set
        assertThat(serverOptions.getTrustOptions()).isNull();
        assertThat(serverOptions.getClientAuth()).isEqualTo(ClientAuth.NONE);
    }

    /**
     * Verifies that the MqttServerOptions for the MQTT server are configured correctly for the use of client
     * certificate based authentication when setting the corresponding properties in the server configuration.
     */
    @Test
    public void testMqttServerConfigWithTlsAndClientAuth() {

        final String keyStorePath = "../core/target/certs/authServerKeyStore.p12";
        final String trustStorePath = "../core/target/certs/trusted-certs.pem";
        final List<String> enabledProtocols = Arrays.asList("TLSv1", "TLSv1.1", "TLSv1.2");

        // GIVEN a protocol gateway with client certificate based authentication (and TLS) configured
        final MqttProtocolGatewayConfig config = new MqttProtocolGatewayConfig();
        config.setKeyStorePath(keyStorePath); // sets KeyCertOptions
        config.setTrustStorePath(trustStorePath); // sets TrustOptions
        config.setSecureProtocols(enabledProtocols);
        config.setSni(true);

        final TestMqttProtocolGateway gateway = createGateway(config);

        // WHEN the server options are created
        final MqttServerOptions serverOptions = gateway.getMqttServerOptions();

        // THEN the trust options are set from the configuration and client certificate based authentication is enabled
        assertThat(serverOptions.getTrustOptions()).isEqualTo(new PemTrustOptions().addCertPath(trustStorePath));
        assertThat(serverOptions.getClientAuth()).isEqualTo(ClientAuth.REQUEST);

        assertThat(serverOptions.isSsl()).isTrue();
        assertThat(serverOptions.getKeyCertOptions()).isEqualTo(new PfxOptions().setPath(keyStorePath));
    }

    /**
     * Verifies that an MQTT server is bound to the configured port and address during startup and
     * {@link AbstractMqttProtocolGateway#afterStartup(Promise)} is being invoked.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartup(final VertxTestContext ctx) {
        final int port = 1111;
        final String bindAddress = "127.0.0.127";

        // GIVEN a protocol gateway with port and address configured
        final MqttProtocolGatewayConfig serverConfig = new MqttProtocolGatewayConfig();
        serverConfig.setPort(port);
        serverConfig.setBindAddress(bindAddress);

        final TestMqttProtocolGateway gateway = createGateway(serverConfig);

        // WHEN starting the verticle
        final Promise<Void> startupTracker = Promise.promise();
        gateway.start(startupTracker);

        // THEN the server starts to listen on the configured port and the start method completes
        startupTracker.future().onComplete(ctx.succeeding(s -> {

            ctx.verify(() -> {
                verify(netServer).listen(eq(port), eq(bindAddress), ProtocolGatewayTestHelper.anyHandler());
                assertThat(gateway.isStartupComplete()).isTrue();
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that an MQTT server is bound to the configured port and address during startup and
     * {@link AbstractMqttProtocolGateway#afterStartup(Promise)} is being invoked.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testServerStopSucceeds(final VertxTestContext ctx) {

        // GIVEN a started protocol gateway
        final TestMqttProtocolGateway gateway = createGateway();

        final Promise<Void> startupTracker = Promise.promise();
        gateway.start(startupTracker);

        startupTracker.future().onComplete(ctx.succeeding(v -> {

            // WHEN stopping the verticle
            final Promise<Void> stopTracker = Promise.promise();
            gateway.stop(stopTracker);

            stopTracker.future().onComplete(ctx.succeeding(ok -> {

                // THEN the MQTT server is closed and the shutdown completes
                ctx.verify(() -> {
                    assertThat(gateway.isShutdownStarted()).isTrue();
                    verify(netServer).close(ProtocolGatewayTestHelper.anyHandler());
                });
                ctx.completeNow();
            }));
        }));

    }

    /**
     * Verifies that the authentication with valid username and password succeeds.
     */
    @Test
    public void testConnectWithUsernamePasswordSucceeds() {

        // GIVEN a protocol gateway
        final AbstractMqttProtocolGateway gateway = createGateway();

        // WHEN connecting with known credentials
        final MqttEndpoint mqttEndpoint = ProtocolGatewayTestHelper.connectMqttEndpoint(gateway,
                TestMqttProtocolGateway.DEVICE_USERNAME,
                TestMqttProtocolGateway.DEVICE_PASSWORD);

        // THEN the connection is accepted
        verify(mqttEndpoint).accept(false);

    }

    /**
     * Verifies that the authentication with invalid username fails.
     */
    @Test
    public void testAuthenticationWithWrongUsernameFails() {

        // GIVEN a protocol gateway
        final TestMqttProtocolGateway gateway = createGateway();

        // WHEN connecting with an unknown user
        final MqttEndpoint mqttEndpoint = ProtocolGatewayTestHelper.connectMqttEndpoint(gateway,
                "unknown-user",
                TestMqttProtocolGateway.DEVICE_PASSWORD);

        // THEN the connection is rejected
        verify(mqttEndpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);

    }

    /**
     * Verifies that the authentication with invalid password fails.
     */
    @Test
    public void testAuthenticationWithWrongPasswordFails() {

        // GIVEN a protocol gateway
        final TestMqttProtocolGateway gateway = createGateway();

        // WHEN connecting with an invalid password
        final MqttEndpoint mqttEndpoint = ProtocolGatewayTestHelper.connectMqttEndpoint(gateway,
                TestMqttProtocolGateway.DEVICE_USERNAME, "wrong-password");

        // THEN the connection is rejected
        verify(mqttEndpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);

    }

    /**
     * Verifies that the authentication with a valid client certificate succeeds.
     */
    @Test
    public void testConnectWithClientCertSucceeds() {

        final X509Certificate deviceCertificate = ProtocolGatewayTestHelper.createCertificate();

        // GIVEN a protocol gateway configured with a trust anchor
        final TestMqttProtocolGateway gateway = new TestMqttProtocolGateway(amqpClientConfig,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            protected Future<Set<TrustAnchor>> getTrustAnchors(final List<X509Certificate> certificates) {
                // verification will always succeed because the client certificate is used as its own trust anchor
                return Future.succeededFuture(Collections.singleton(new TrustAnchor(deviceCertificate, null)));
            }
        };

        // WHEN connecting with a client certificate that can be validated by the trust anchor
        final MqttEndpoint endpoint = ProtocolGatewayTestHelper.connectMqttEndpointWithClientCertificate(gateway,
                deviceCertificate);

        // THEN the connection is accepted
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that the authentication with an invalid client certificate fails.
     */
    @Test
    public void testAuthenticationWithClientCertFailsIfTrustAnchorDoesNotMatch() {

        // GIVEN a protocol gateway configured with a trust anchor
        final TestMqttProtocolGateway gateway = new TestMqttProtocolGateway(amqpClientConfig,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            protected Future<Set<TrustAnchor>> getTrustAnchors(final List<X509Certificate> certificates) {
                // verification will fail because the certificate used for the trust anchor has nothing to do with the
                // client certificate
                final X509Certificate newCertificate = ProtocolGatewayTestHelper.createCertificate();
                return Future.succeededFuture(Collections.singleton(new TrustAnchor(newCertificate, null)));
            }
        };

        // WHEN connecting with a client certificate that can NOT be validated by the trust anchor
        final X509Certificate deviceCertificate = ProtocolGatewayTestHelper.createCertificate();
        final MqttEndpoint endpoint = ProtocolGatewayTestHelper
                .connectMqttEndpointWithClientCertificate(gateway, deviceCertificate);

        // THEN the connection is rejected
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
    }

    /**
     * Verifies that the MQTT connection fails if the Hono instance is not available.
     */
    @Test
    public void testConnectFailsWhenGatewayCouldNotConnect() {

        // GIVEN a protocol gateway where establishing a connection to Hono's AMQP adapter fails
        when(amqpAdapterClientFactory.connect()).thenReturn(Future.failedFuture("Connect failed"));

        final TestMqttProtocolGateway gateway = createGateway();

        // WHEN a device connects
        final MqttEndpoint endpoint = connectTestDevice(gateway);

        // THEN the connection is rejected
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    }

    /**
     * Verifies that the credentials for the gateway provided by the implementation of
     * {@link AbstractMqttProtocolGateway} are used to configure the connection to the AMQP adapter, if no credentials
     * are provided in the client configuration.
     */
    @Test
    public void testConnectWithGatewayCredentialsResolvedDynamicallySucceeds() {

        // GIVEN a protocol gateway where the AMQP config does NOT contain credentials ...
        // ... and where the gateway credentials are resolved by the implementation
        final ClientConfigProperties configWithoutCredentials = new ClientConfigProperties();
        final AbstractMqttProtocolGateway gateway = new TestMqttProtocolGateway(configWithoutCredentials,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            AmqpAdapterClientFactory createTenantClientFactory(final String tenantId,
                    final ClientConfigProperties clientConfig) {

                // THEN the AMQP connection is authenticated with the provided credentials...
                assertThat(clientConfig.getUsername()).isEqualTo(GW_USERNAME);
                assertThat(clientConfig.getPassword()).isEqualTo(GW_PASSWORD);

                // ... and not with the credentials from the configuration
                assertThat(clientConfig.getUsername()).isNotEqualTo(configWithoutCredentials.getUsername());
                assertThat(clientConfig.getPassword()).isNotEqualTo(configWithoutCredentials.getPassword());

                return super.createTenantClientFactory(tenantId, clientConfig);
            }
        };

        // WHEN the gateway connects
        connectTestDevice(gateway);

    }

    /**
     * Verifies that the credentials for the gateway provided by the client configuration are used to configure the
     * connection to the AMQP adapter and take precedence over the ones provided by the implementation of
     * {@link AbstractMqttProtocolGateway}.
     */
    @Test
    public void testConfiguredCredentialsTakePrecedenceOverImplementation() {

        final String username = "a-user";
        final String password = "a-password";
        final ClientConfigProperties configWithCredentials = new ClientConfigProperties();
        configWithCredentials.setUsername(username);
        configWithCredentials.setPassword(password);

        // GIVEN a protocol gateway where the AMQP config does contains credentials
        final AbstractMqttProtocolGateway gateway = new TestMqttProtocolGateway(configWithCredentials,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            AmqpAdapterClientFactory createTenantClientFactory(final String tenantId,
                    final ClientConfigProperties clientConfig) {

                // THEN the AMQP connection is authenticated with the configured credentials...
                assertThat(clientConfig.getUsername()).isEqualTo(username);
                assertThat(clientConfig.getPassword()).isEqualTo(password);

                // ... and not with the credentials from the implementation
                assertThat(clientConfig.getUsername()).isNotEqualTo(GW_USERNAME);
                assertThat(clientConfig.getPassword()).isNotEqualTo(GW_PASSWORD);

                return super.createTenantClientFactory(tenantId, clientConfig);
            }
        };

        // WHEN the gateway connects
        connectTestDevice(gateway);

    }

    /**
     * Verifies that the downstream message constructed in
     * {@link AbstractMqttProtocolGateway#onPublishedMessage(MqttDownstreamContext)} is set completely into the AMQP
     * message sent downstream.
     */
    @Test
    public void testDownstreamMessage() {

        final String payload = "payload1";
        final String topic = "topic/1";

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // GIVEN a protocol gateway with a MQTT endpoint connected
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a MQTT message
        ProtocolGatewayTestHelper.sendMessage(mqttEndpoint, Buffer.buffer(payload), topic);

        // THEN the AMQP message contains the payload, application properties and content type
        verify(protonSender).send(messageCaptor.capture(), any());

        final Message amqpMessage = messageCaptor.getValue();

        assertThat(MessageHelper.getPayloadAsString(amqpMessage)).isEqualTo(payload);

        assertThat(MessageHelper.getApplicationProperty(amqpMessage.getApplicationProperties(),
                TestMqttProtocolGateway.KEY_APPLICATION_PROPERTY_TOPIC, String.class)).isEqualTo(topic);
        assertThat(MessageHelper.getDeviceId(amqpMessage)).isEqualTo(TestMqttProtocolGateway.DEVICE_ID);

        assertThat(amqpMessage.getContentType()).isEqualTo(TestMqttProtocolGateway.CONTENT_TYPE);
    }

    /**
     * Verifies that an event message is being sent to the right address.
     */
    @Test
    public void testEventMessage() {

        final String payload = "payload1";
        final String topic = "topic/1";

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // GIVEN a protocol gateway that sends every MQTT publish message as an event downstream and a connected MQTT
        // endpoint
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a MQTT message
        ProtocolGatewayTestHelper.sendMessage(mqttEndpoint, Buffer.buffer(payload), topic);

        // THEN the AMQP message contains the correct address
        verify(protonSender).send(messageCaptor.capture(), any());

        final String expectedAddress = "event/" + TestMqttProtocolGateway.TENANT_ID + "/"
                + TestMqttProtocolGateway.DEVICE_ID;
        assertThat(messageCaptor.getValue().getAddress()).isEqualTo(expectedAddress);

    }

    /**
     * Verifies that a telemetry message is being sent to the right address.
     */
    @Test
    public void testTelemetryMessage() {

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // GIVEN a protocol gateway that sends every MQTT publish messages as telemetry messages downstream and a
        // connected MQTT endpoint
        final TestMqttProtocolGateway gateway = new TestMqttProtocolGateway(amqpClientConfig,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            protected Future<DownstreamMessage> onPublishedMessage(final MqttDownstreamContext ctx) {
                return Future.succeededFuture(new TelemetryMessage(ctx.message().payload(), false));
            }
        };

        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a MQTT message
        ProtocolGatewayTestHelper.sendMessage(mqttEndpoint, Buffer.buffer("payload"), "topic");

        // THEN the AMQP message contains the correct address
        verify(protonSender).send(messageCaptor.capture(), any());

        final String expectedAddress = "telemetry/" + TestMqttProtocolGateway.TENANT_ID + "/"
                + TestMqttProtocolGateway.DEVICE_ID;
        assertThat(messageCaptor.getValue().getAddress()).isEqualTo(expectedAddress);
    }

    /**
     * Verifies that a command response message is constructed correctly and being sent to the right address.
     */
    @Test
    public void testCommandResponse() {

        final String payload = "payload1";
        final String correlationId = "the-correlation-id";
        final String replyId = "the-reply-id";
        final Integer status = 200;

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // GIVEN a protocol gateway that sends every MQTT publish messages as command response messages downstream and a
        // connected MQTT endpoint
        final TestMqttProtocolGateway gateway = new TestMqttProtocolGateway(amqpClientConfig,
                new MqttProtocolGatewayConfig(), vertx, amqpAdapterClientFactory) {

            @Override
            protected Future<DownstreamMessage> onPublishedMessage(final MqttDownstreamContext ctx) {
                return Future.succeededFuture(
                        new CommandResponseMessage(replyId, correlationId, status.toString(), ctx.message().payload()));
            }
        };

        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a MQTT message
        ProtocolGatewayTestHelper.sendMessage(mqttEndpoint, Buffer.buffer(payload), "topic/123");

        // THEN the AMQP message contains the required values and the correct address
        verify(protonSender).send(messageCaptor.capture(), any());

        final Message amqpMessage = messageCaptor.getValue();

        assertThat(MessageHelper.getPayloadAsString(amqpMessage)).isEqualTo(payload);
        assertThat(amqpMessage.getCorrelationId()).isEqualTo(correlationId);
        assertThat(MessageHelper.getApplicationProperty(amqpMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_STATUS, Integer.class)).isEqualTo(status);

        final String expectedAddress = "command_response/" + TestMqttProtocolGateway.TENANT_ID + "/"
                + TestMqttProtocolGateway.DEVICE_ID + "/" + replyId;
        assertThat(amqpMessage.getAddress()).isEqualTo(expectedAddress);
    }

    /**
     * Verifies that subscriptions are stored and acknowledged correctly.
     */
    @Test
    public void testCommandSubscription() {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<MqttQoS>> subscribeAckCaptor = ArgumentCaptor.forClass(List.class);

        // GIVEN a protocol gateway and a connected MQTT endpoint
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a subscribe message with multiple topic filters
        final int subscribeMsgId = ProtocolGatewayTestHelper.subscribe(mqttEndpoint,
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER1, MqttQoS.AT_LEAST_ONCE),
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER2, MqttQoS.AT_MOST_ONCE));

        // THEN the subscriptions are acknowledged correctly...
        verify(mqttEndpoint).subscribeAcknowledge(eq(subscribeMsgId), subscribeAckCaptor.capture());

        assertThat(subscribeAckCaptor.getValue()).isEqualTo(Arrays.asList(MqttQoS.AT_LEAST_ONCE, MqttQoS.AT_MOST_ONCE));

        // ... and the internal map is correct as well
        final Map<String, CommandSubscription> subscriptions = gateway.getCommandSubscriptionsManager().getSubscriptions();

        assertThat(subscriptions.size()).isEqualTo(2);
        assertThat(subscriptions.get(TestMqttProtocolGateway.FILTER1).getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(subscriptions.get(TestMqttProtocolGateway.FILTER2).getQos()).isEqualTo(MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Verifies that when a device tries to subscribe using the unsupported QoS 2, then it is only granted QoS 1.
     */
    @Test
    public void testCommandSubscriptionDowngradesQoS2() {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<MqttQoS>> subscribeAckCaptor = ArgumentCaptor.forClass(List.class);

        // GIVEN a protocol gateway and a connected MQTT endpoint
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a subscribe message that requests QoS 2
        final int subscribeMsgId = ProtocolGatewayTestHelper.subscribe(mqttEndpoint,
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER1, MqttQoS.EXACTLY_ONCE));

        // THEN the QoS is downgraded to QoS 1 in the acknowledgement...
        verify(mqttEndpoint).subscribeAcknowledge(eq(subscribeMsgId), subscribeAckCaptor.capture());

        assertThat(subscribeAckCaptor.getValue()).isEqualTo(Collections.singletonList(MqttQoS.AT_LEAST_ONCE));

        // ... and in the internal map as well
        final Map<String, CommandSubscription> subscriptions = gateway.getCommandSubscriptionsManager().getSubscriptions();

        assertThat(subscriptions.get(TestMqttProtocolGateway.FILTER1).getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
    }

    /**
     * Verifies that no subscriptions are being accepted for unsupported topic filters.
     */
    @Test
    public void testCommandSubscriptionFailsForInvalidTopicFilter() {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<MqttQoS>> subscribeAckCaptor = ArgumentCaptor.forClass(List.class);

        // GIVEN a protocol gateway and a connected MQTT endpoint
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        // WHEN sending a subscribe message with a topic filter that the gateway does not provide
        final int subscribeMsgId = ProtocolGatewayTestHelper.subscribe(mqttEndpoint,
                TestMqttProtocolGateway.FILTER_INVALID);

        // THEN the subscription is acknowledged correctly as a failure...
        verify(mqttEndpoint).subscribeAcknowledge(eq(subscribeMsgId), subscribeAckCaptor.capture());

        assertThat(subscribeAckCaptor.getValue()).isEqualTo(Collections.singletonList(MqttQoS.FAILURE));

        // ... and it is not contained in the internal map
        final Map<String, CommandSubscription> subscriptions = gateway.getCommandSubscriptionsManager().getSubscriptions();

        assertThat(subscriptions.isEmpty()).isTrue();
    }

    /**
     * Verifies that when the protocol gateway receives a command for a subscribed device, then the command is published
     * via MQTT to the device.
     */
    @Test
    public void testReceiveCommand() {
        final String subject = "the/subject";
        final String replyTo = "the/reply/address";
        final String correlationId = "the-correlation-id";
        final String messageId = "the-message-id";

        final Message commandMessage = new MessageImpl();
        MessageHelper.setJsonPayload(commandMessage, TestMqttProtocolGateway.PAYLOAD);
        commandMessage.setSubject(subject);
        commandMessage.setReplyTo(replyTo);
        commandMessage.setCorrelationId(correlationId);
        commandMessage.setMessageId(messageId);

        final JsonObject expected = new JsonObject()
                .put(TestMqttProtocolGateway.KEY_SUBJECT, subject)
                .put(TestMqttProtocolGateway.KEY_REPLY_TO, replyTo)
                .put(TestMqttProtocolGateway.KEY_CORRELATION_ID, correlationId)
                .put(TestMqttProtocolGateway.KEY_MESSAGE_ID, messageId)
                .put(TestMqttProtocolGateway.KEY_COMMAND_PAYLOAD, TestMqttProtocolGateway.PAYLOAD)
                .put(TestMqttProtocolGateway.KEY_CONTENT_TYPE, TestMqttProtocolGateway.CONTENT_TYPE);

        // GIVEN a protocol gateway and a connected MQTT endpoint with a command subscription
        final TestMqttProtocolGateway gateway = createGateway();

        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        ProtocolGatewayTestHelper.subscribe(mqttEndpoint, TestMqttProtocolGateway.FILTER1);

        // WHEN receiving the command
        commandHandler.accept(commandMessage);

        // THEN the command is published to the MQTT endpoint
        final ArgumentCaptor<Buffer> payloadCaptor = ArgumentCaptor.forClass(Buffer.class);

        verify(mqttEndpoint).publish(eq(TestMqttProtocolGateway.COMMAND_TOPIC), payloadCaptor.capture(),
                eq(MqttQoS.AT_LEAST_ONCE), eq(false), eq(false), any());

        assertThat(payloadCaptor.getValue().toJsonObject()).isEqualTo(expected);

    }

    /**
     * Verifies that subscriptions are remove when unsubscribing.
     */
    @Test
    public void testUnsubscribe() {

        // GIVEN a protocol gateway and a connected MQTT endpoint with two subscriptions
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        ProtocolGatewayTestHelper.subscribe(mqttEndpoint,
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER1, MqttQoS.AT_LEAST_ONCE),
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER2, MqttQoS.AT_MOST_ONCE));

        // WHEN sending an unsubscribe message containing one of the topic filters and a third onw
        final int unsubscribeMsgId = ProtocolGatewayTestHelper.unsubscribe(mqttEndpoint,
                TestMqttProtocolGateway.FILTER2, TestMqttProtocolGateway.FILTER_INVALID);

        // THEN the message is acknowledged
        verify(mqttEndpoint).unsubscribeAcknowledge(eq(unsubscribeMsgId));

        // ... and the internal map is correct as well
        final Map<String, CommandSubscription> subscriptions = gateway.getCommandSubscriptionsManager().getSubscriptions();
        assertThat(subscriptions.size()).isEqualTo(1);
        assertThat(subscriptions.containsKey(TestMqttProtocolGateway.FILTER1)).isTrue();
        assertThat(subscriptions.containsKey(TestMqttProtocolGateway.FILTER2)).isFalse();

    }

    /**
     * Verifies that when the MQTT connections is being closed, the subscriptions are removed and
     * {@link AbstractMqttProtocolGateway#onDeviceConnectionClose(MqttEndpoint)} is invoked.
     */
    @Test
    public void testConnectionClose() {

        // GIVEN a protocol gateway and a connected MQTT endpoint with subscriptions
        final TestMqttProtocolGateway gateway = createGateway();
        final MqttEndpoint mqttEndpoint = connectTestDevice(gateway);

        ProtocolGatewayTestHelper.subscribe(mqttEndpoint,
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER1, MqttQoS.AT_LEAST_ONCE),
                new MqttTopicSubscription(TestMqttProtocolGateway.FILTER2, MqttQoS.AT_MOST_ONCE));

        // WHEN the connection is closed
        mqttEndpoint.close();

        // THEN the subscriptions are removed ...
        assertThat(gateway.getCommandSubscriptionsManager().getSubscriptions().isEmpty()).isTrue();

        // ... and the callback onDeviceConnectionClose() has been invoked
        assertThat(gateway.isConnectionClosed()).isTrue();
    }

    /**
     * Creates a mocked Hono connection that returns a Noop Tracer.
     *
     * @param vertx The vert.x instance to use.
     * @param props The client properties to use.
     * @return The connection.
     */
    private HonoConnection mockHonoConnection(final Vertx vertx, final ClientConfigProperties props) {

        final Tracer tracer = NoopTracerFactory.create();
        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(connection.getConfig()).thenReturn(props);
        when(connection.getTracer()).thenReturn(tracer);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.executeOnContext(ProtocolGatewayTestHelper.anyHandler())).then(invocation -> {
            final Promise<?> result = Promise.promise();
            final Handler<Future<?>> handler = invocation.getArgument(0);
            handler.handle(result.future());
            return result.future();
        });

        when(connection.getTracer()).thenReturn(tracer);
        when(connection.createSender(any(), any(), any())).thenReturn(Future.succeededFuture(protonSender));

        final ProtonReceiver receiver = mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));

        return connection;
    }

    /**
     * Creates a mocked Proton sender which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked sender.
     */
    private ProtonSender mockProtonSender() {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        when(sender.getTarget()).thenReturn(mock(Target.class));

        return sender;
    }

    /**
     * Creates a mocked Proton receiver which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked receiver.
     */
    public ProtonReceiver mockProtonReceiver() {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        when(receiver.getSource()).thenReturn(new Source());

        return receiver;
    }

    private void setCommandHandler(final Consumer<Message> msgHandler) {
        commandHandler = msgHandler;
    }

    private MqttEndpoint connectTestDevice(final AbstractMqttProtocolGateway gateway) {
        return ProtocolGatewayTestHelper.connectMqttEndpoint(gateway,
                TestMqttProtocolGateway.DEVICE_USERNAME,
                TestMqttProtocolGateway.DEVICE_PASSWORD);
    }

    private TestMqttProtocolGateway createGateway() {
        return createGateway(new MqttProtocolGatewayConfig());
    }

    private TestMqttProtocolGateway createGateway(final MqttProtocolGatewayConfig gatewayServerConfig) {
        return new TestMqttProtocolGateway(amqpClientConfig, gatewayServerConfig, vertx, amqpAdapterClientFactory);
    }

}
