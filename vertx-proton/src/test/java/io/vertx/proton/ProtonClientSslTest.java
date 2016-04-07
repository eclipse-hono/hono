package io.vertx.proton;

import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ProtonClientSslTest {

    private static Logger LOG = LoggerFactory.getLogger(ProtonClientSslTest.class);

    private static final String PASSWORD = "password";
    private static final String KEYSTORE = "src/test/resources/broker-pkcs12.keystore";
    private static final String TRUSTSTORE = "src/test/resources/client-pkcs12.truststore";
    private static final String KEYSTORE_CLIENT = "src/test/resources/client-pkcs12.keystore";
    private static final String OTHER_CA_TRUSTSTORE = "src/test/resources/other-ca-pkcs12.truststore";

    private Vertx vertx;
    private ProtonServer protonServer;

    @Before
    public void setup() {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown() {
        try {
            vertx.close();
        } finally {
            if(protonServer != null) {
                protonServer.close();
            }
        }
    }

    @Test(timeout = 20000)
    public void testConnectWithSslSucceeds(TestContext context) throws Exception {
        Async async = context.async();

        // Create a server that accept a connection and expects a client connection+session+receiver
        ProtonServerOptions serverOptions = new ProtonServerOptions();
        serverOptions.setSsl(true);
        PfxOptions serverPfxOptions = new PfxOptions().setPath(KEYSTORE).setPassword(PASSWORD);
        serverOptions.setPfxKeyCertOptions(serverPfxOptions);

        protonServer = createServer(serverOptions, this::handleClientConnectionSessionReceiverOpen);


        // Connect the client and open a receiver to verify the connection works
        ProtonClientOptions clientOptions = new ProtonClientOptions();
        clientOptions.setSsl(true);
        PfxOptions clientPfxOptions = new PfxOptions().setPath(TRUSTSTORE).setPassword(PASSWORD);
        clientOptions.setPfxTrustOptions(clientPfxOptions);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(clientOptions, "localhost", protonServer.actualPort(), res -> {
            // Expect connect to succeed
            context.assertTrue(res.succeeded());
            ProtonConnection connection = res.result();
            connection.open();

            ProtonReceiver receiver = connection.createReceiver("some-address");

            receiver.openHandler(recvResult -> {
                context.assertTrue(recvResult.succeeded());
                LOG.trace("Client reciever open");
                async.complete();
            })
            .open();
        });

        async.awaitSuccess();
    }

    @Test(timeout = 20000)
    public void testConnectWithSslToNonSslServerFails(TestContext context) throws Exception {
        Async async = context.async();

        // Create a server that doesn't use ssl
        ProtonServerOptions serverOptions = new ProtonServerOptions();
        serverOptions.setSsl(false);

        protonServer = createServer(serverOptions, this::handleClientConnectionSessionReceiverOpen);


        // Try to connect the client and expect it to fail
        ProtonClientOptions clientOptions = new ProtonClientOptions();
        clientOptions.setSsl(true);
        PfxOptions pfxOptions = new PfxOptions().setPath(TRUSTSTORE).setPassword(PASSWORD);
        clientOptions.setPfxTrustOptions(pfxOptions);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(clientOptions, "localhost", protonServer.actualPort(), res -> {
            // Expect connect to fail due to remote peer not doing SSL
            context.assertFalse(res.succeeded());
            async.complete();
        });

        async.awaitSuccess();
    }

    @Test(timeout = 20000)
    public void testConnectWithSslToServerWithUntrustedKeyFails(TestContext context) throws Exception {
        Async async = context.async();

        // Create a server that accept a connection and expects a client connection+session+receiver
        ProtonServerOptions serverOptions = new ProtonServerOptions();
        serverOptions.setSsl(true);
        PfxOptions serverPfxOptions = new PfxOptions().setPath(KEYSTORE).setPassword(PASSWORD);
        serverOptions.setPfxKeyCertOptions(serverPfxOptions);

        protonServer = createServer(serverOptions, this::handleClientConnectionSessionReceiverOpen);


        // Try to connect the client and expect it to fail due to us not trusting the server
        ProtonClientOptions clientOptions = new ProtonClientOptions();
        clientOptions.setSsl(true);
        PfxOptions pfxOptions = new PfxOptions().setPath(OTHER_CA_TRUSTSTORE).setPassword(PASSWORD);
        clientOptions.setPfxTrustOptions(pfxOptions);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(clientOptions, "localhost", protonServer.actualPort(), res -> {
            // Expect connect to fail due to remote peer not doing SSL
            context.assertFalse(res.succeeded());
            async.complete();
        });

        async.awaitSuccess();
    }

    @Test(timeout = 20000)
    public void testConnectWithSslToServerWhileUsingTrustAll(TestContext context) throws Exception {
        Async async = context.async();

        // Create a server that accept a connection and expects a client connection+session+receiver
        ProtonServerOptions serverOptions = new ProtonServerOptions();
        serverOptions.setSsl(true);
        PfxOptions serverPfxOptions = new PfxOptions().setPath(KEYSTORE).setPassword(PASSWORD);
        serverOptions.setPfxKeyCertOptions(serverPfxOptions);

        protonServer = createServer(serverOptions, this::handleClientConnectionSessionReceiverOpen);


        // Try to connect the client and expect it to succeed due to trusting all certs
        ProtonClientOptions clientOptions = new ProtonClientOptions();
        clientOptions.setSsl(true);
        clientOptions.setTrustAll(true);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(clientOptions, "localhost", protonServer.actualPort(), res -> {
            // Expect connect to succeed
            context.assertTrue(res.succeeded());
            async.complete();
        });

        async.awaitSuccess();
    }

    @Test(timeout = 20000)
    public void testConnectWithSslWithoutRequiredClientKeyFails(TestContext context) throws Exception {
        doClientCertificateTestImpl(context, false);
    }

    @Test(timeout = 20000)
    public void testConnectWithSslWithRequiredClientKeySucceeds(TestContext context) throws Exception {
        doClientCertificateTestImpl(context, true);
    }

    private void doClientCertificateTestImpl(TestContext context, boolean supplyClientCert) throws InterruptedException, ExecutionException {
        Async async = context.async();

        // Create a server that accept a connection and expects a client connection+session+receiver
        ProtonServerOptions serverOptions = new ProtonServerOptions();
        serverOptions.setSsl(true);
        serverOptions.setClientAuth(ClientAuth.REQUIRED);
        PfxOptions serverPfxOptions = new PfxOptions().setPath(KEYSTORE).setPassword(PASSWORD);
        serverOptions.setPfxKeyCertOptions(serverPfxOptions);

        PfxOptions pfxOptions = new PfxOptions().setPath(TRUSTSTORE).setPassword(PASSWORD);
        serverOptions.setPfxTrustOptions(pfxOptions);

        protonServer = createServer(serverOptions, this::handleClientConnectionSessionReceiverOpen);

        // Try to connect the client
        ProtonClientOptions clientOptions = new ProtonClientOptions();
        clientOptions.setSsl(true);
        clientOptions.setPfxTrustOptions(pfxOptions);

        if(supplyClientCert) {
            PfxOptions clientKeyPfxOptions = new PfxOptions().setPath(KEYSTORE_CLIENT).setPassword(PASSWORD);
            clientOptions.setPfxKeyCertOptions(clientKeyPfxOptions);
        }

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(clientOptions, "localhost", protonServer.actualPort(), res -> {
            if(supplyClientCert) {
                // Expect connect to succeed
                context.assertTrue(res.succeeded());
            } else {
                // Expect connect to fail
                context.assertFalse(res.succeeded());
            }
            async.complete();
        });

        async.awaitSuccess();
    }

    private ProtonServer createServer(ProtonServerOptions serverOptions, Handler<ProtonConnection> serverConnHandler) throws InterruptedException, ExecutionException {
        ProtonServer server = ProtonServer.create(vertx, serverOptions);

        server.connectHandler(serverConnHandler);

        FutureHandler<ProtonServer, AsyncResult<ProtonServer>> handler = FutureHandler.asyncResult();
        server.listen(0, handler);
        handler.get();

        return server;
    }

    private void handleClientConnectionSessionReceiverOpen(ProtonConnection serverConnection) {
        // Expect a session to open, when the receiver is created by the client
        serverConnection.sessionOpenHandler(serverSession -> {
            LOG.trace("Server session open");
            serverSession.open();
        });
        // Expect a sender link, then close the session after opening it.
        serverConnection.senderOpenHandler(serverSender -> {
            LOG.trace("Server sender open");
            serverSender.open();
        });

        serverConnection.openHandler(serverSender -> {
            LOG.trace("Server connection open");
            serverConnection.open();
        });
    }
}
