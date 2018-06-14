package org.eclipse.hono.adapter.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.command.CommandConnection;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;

/**
 * Verifies the behaviour of {@link VertxBasedAmqpProtocolAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedAmqpProtocolAdapterTest {

    @Rule
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    private HonoClient tenantServiceClient;
    private HonoClient credentialsServiceClient;
    private HonoClient messagingServiceClient;
    private HonoClient registrationServiceClient;
    private CommandConnection commandConnection;

    private RegistrationClient registrationClient;
    private TenantClient tenantClient;

    private ProtocolAdapterProperties config;

    /**
     * Setups the protocol adapter.
     * 
     * @param context The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup(final TestContext context) {

        tenantClient = mock(TenantClient.class);

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingServiceClient = mock(HonoClient.class);
        when(messagingServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(messagingServiceClient));

        registrationClient = mock(RegistrationClient.class);
        final JsonObject regAssertion = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "assert-token");
        when(registrationClient.assertRegistration(anyString(), any()))
                .thenReturn(Future.succeededFuture(regAssertion));

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(registrationServiceClient));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(registrationClient));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));

        config = new ProtocolAdapterProperties();
        config.setAuthenticationRequired(false);
        config.setInsecurePort(4040);
    }

    /**
     * Verifies that a client provided Proton server instance is used and started by the adapter instead of
     * creating/starting a new one.
     * 
     * @param ctx The test context to use for running asynchronous tests.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartUsesClientProvidedAmqpServer(final TestContext ctx) {
        // GIVEN an adapter with a client provided Amqp Server
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(result -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the client provided server is started
        startup.await();
        verify(server).connectHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
    }

    /**
     * Verifies that the AMQP Adapter does not support anonymous relay.
     */
    @Test
    public void testAnonymousRelayNotSupported() {
        // GIVEN an AMQP adapter with a configured server.
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        // WHEN the adapter receives a request from a link
        // that does not specify a target address
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        adapter.handleRemoteReceiverOpen(receiver, mock(ProtonConnection.class));

        // THEN the adapter closes the link.
        verify(receiver).close();
    }

    /**
     * Verifies that a request to upload telemetry message (with settled=true) results in the sender sending the message
     * without waiting for a response from the downstream peer.
     * 
     * AT_MOST_ONCE delivery semantics
     */
    @Test
    public void uploadTelemetryMessageWithSettledDeliverySemantics() {
        // GIVEN an AMQP adapter with a configured server
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final MessageSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant
        givenAConfiguredTenant("some-tenant", true);

        // IF a device sends a 'fire and forget' telemetry message
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(true);

        final ResourceIdentifier resource = ResourceIdentifier.from("telemetry", "some-tenant", "the-device");
        adapter.uploadMessage(new AmqpContext(delivery, getFakeMessage(), resource));

        // THEN the adapter sends the message and does not wait for response from the peer.
        verify(telemetrySender).send(any(Message.class));
    }

    /**
     * Verifies that a request to upload telemetry message (with settled=false) results in the sender sending the
     * message and waits for a response from the downstream peer.
     * 
     * AT_LEAST_ONCE delivery semantics.
     */
    @Test
    public void uploadTelemetryMessageWithUnsettledDeliverySemantics() {
        // GIVEN an adapter configured to use a user-define server.
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final MessageSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant
        givenAConfiguredTenant("enabled-tenant", true);

        // IF a device send telemetry data (with un-settled delivery)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false);
        final ResourceIdentifier resource = ResourceIdentifier.from("telemetry", "enabled-tenant", "the-device");
        adapter.uploadMessage(new AmqpContext(delivery, getFakeMessage(), resource));

        // THEN the sender sends the message and waits for the outcome from the downstream peer
        verify(telemetrySender).sendAndWaitForOutcome(any(Message.class));
    }

    private void givenAConfiguredTenant(final String tenantId, final boolean enabled) {
        final TenantObject tenantConfig = TenantObject.from(tenantId, enabled);
        if (!enabled) {
            tenantConfig.addAdapterConfiguration(new JsonObject()
                    .put(TenantConstants.FIELD_ADAPTERS_TYPE, "amqp")
                    .put(TenantConstants.FIELD_ENABLED, enabled));
        }
        when(tenantClient.get(tenantId)).thenReturn(Future.succeededFuture(tenantConfig));
    }

    private Message getFakeMessage() {
        final Message message = mock(Message.class);
        when(message.getContentType()).thenReturn("application/text");
        when(message.getBody()).thenReturn(new AmqpValue("some payload"));
        return message;
    }

    private MessageSender givenATelemetrySenderForAnyTenant() {
        final MessageSender sender = mock(MessageSender.class);
        when(messagingServiceClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    /**
     * Gets an AMQP adapter configured to use a given server.
     * 
     * @return The AMQP adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter givenAnAmqpAdapter() {
        final ProtonServer server = getAmqpServer();
        return getAdapter(server);
    }

    /**
     * Creates a protocol adapter for a given AMQP Proton server.
     * 
     * @param server The AMQP Proton server.
     * @return The AMQP adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter getAdapter(final ProtonServer server) {
        final VertxBasedAmqpProtocolAdapter adapter = new VertxBasedAmqpProtocolAdapter();

        adapter.setConfig(config);
        adapter.setInsecureAmqpServer(server);
        adapter.setTenantServiceClient(tenantServiceClient);
        adapter.setHonoMessagingClient(messagingServiceClient);
        adapter.setRegistrationServiceClient(registrationServiceClient);
        adapter.setCredentialsServiceClient(credentialsServiceClient);
        adapter.setCommandConnection(commandConnection);
        return adapter;
    }

    /**
     * Creates and sets up a ProtonServer instance.
     *
     * @return The configured server instance.
     */
    @SuppressWarnings("unchecked")
    private ProtonServer getAmqpServer() {
        final ProtonServer server = mock(ProtonServer.class);
        when(server.actualPort()).thenReturn(0, 4040);
        when(server.connectHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            final Handler<AsyncResult<ProtonServer>> handler = (Handler<AsyncResult<ProtonServer>>) invocation.getArgument(0);
            handler.handle(Future.succeededFuture(server));
            return server;
        });
        return server;
    }
}
