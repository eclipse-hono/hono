/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.impl.ProtonServerImpl;

import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.proton.ProtonHelper.accepted;
import static io.vertx.proton.ProtonHelper.message;
import static io.vertx.proton.ProtonHelper.tag;

@RunWith(VertxUnitRunner.class)
public class ProtonClientTest extends MockServerTestBase {

    private static Logger LOG = LoggerFactory.getLogger(ProtonClientTest.class);

    @Test(timeout = 20000)
    public void testConnectionOpenResultReturnsConnection(TestContext context) {
        Async async = context.async();
        connect(context, connectedConn -> {
            connectedConn.openHandler(result -> {
                context.assertTrue(result.succeeded());

                ProtonConnection openedConn = result.result();
                context.assertNotNull(openedConn, "opened connection result should not be null");
                openedConn.disconnect();
                async.complete();
            })
            .open();
        });
    }

    @Test(timeout = 20000)
    public void testClientIdentification(TestContext context) {
        Async async = context.async();
        connect(context, connection -> {
            connection
                .setContainer("foo")
                .openHandler(x -> {
                    context.assertEquals("foo", connection.getContainer());
                    // Our mock server responds with a pong container id
                    context.assertEquals("pong: foo", connection.getRemoteContainer());
                    connection.disconnect();
                    async.complete();
                })
                .open();
        });
    }

    @Test(timeout = 20000)
    public void testRemoteDisconnectHandling(TestContext context) {
        Async async = context.async();
        connect(context, connection->{
            connection.open();
            context.assertFalse(connection.isDisconnected());
            connection.disconnectHandler(x ->{
                context.assertTrue(connection.isDisconnected());
                async.complete();
            });

            // Send a request to the server for him to disconnect us
            ProtonSender sender = connection.createSender(null).open();
            sender.send(tag(""), message("command", "disconnect"));
        });
    }

    @Test(timeout = 20000)
    public void testLocalDisconnectHandling(TestContext context) {
        Async async = context.async();
        connect(context, connection -> {
            context.assertFalse(connection.isDisconnected());
            connection.disconnectHandler(x -> {
                context.assertTrue(connection.isDisconnected());
                async.complete();
            });
            // We will force the disconnection to the server
            connection.disconnect();
        });
    }

    @Test(timeout = 20000)
    public void testRequestResponse(TestContext context) {
        sendReceiveEcho(context, "Hello World");
    }

    @Test(timeout = 20000)
    public void testTransferLargeMessage(TestContext context) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 1024*1024*5; i++) {
            builder.append('a'+(i%26));
        }
        sendReceiveEcho(context, builder.toString());
    }

    private void sendReceiveEcho(TestContext context, String data) {
        Async async = context.async();
        connect(context, connection -> {
            connection.open();
            connection.createReceiver(MockServer.Addresses.echo.toString())
                .handler((d, m) -> {
                    String actual = (String) (getMessageBody(context, m));
                    context.assertEquals(data, actual);
                    connection.disconnect();
                    async.complete();
                })
                .flow(10)
                .open();

            connection.createSender(MockServer.Addresses.echo.toString())
                .open()
                .send(tag(""), message("echo", data));


        });
    }

    @Test(timeout = 20000)
    public void testReceiverAsyncSettle(TestContext context) {
        Async async = context.async();
        connect(context, connection -> {
            connection.open();
            AtomicInteger counter = new AtomicInteger(0);
            AtomicBoolean msgAccepted = new AtomicBoolean();
            ProtonReceiver receiver = connection.createReceiver(MockServer.Addresses.two_messages.toString());

            // Set the receiver not to auto-accept messages after the handler runs
            // and in turn not auto-settle, or replenish the credit used. We will
            // accept+settle the message manually at a later point.
            receiver.setAutoAccept(false);

            receiver.handler((d, m) -> {
                    int count = counter.incrementAndGet();
                    switch (count) {
                        case 1: {
                            validateMessage(context, count, "Hello", m);

                            // On 1st message
                            // lets delay the settlement and credit..
                            vertx.setTimer(1000, x -> {
                                accepted(d, true);
                                msgAccepted.set(true);
                            });

                            // We only flowed 1 credit, so we should not get
                            // another message until we run the settle callback
                            // above and issue another credit to the sender.
                            vertx.setTimer(500, x -> {
                                context.assertEquals(1, counter.get());
                            });
                            break;
                        }
                        case 2: {
                            validateMessage(context, count, "World", m);
                            context.assertTrue(msgAccepted.get(), "Earlier message was not yet accepted+settled,"
                                    + " should not have recieved message 2 yet!");

                            // On 2nd message.. lets finish the test..
                            async.complete();
                            connection.disconnect();
                            break;
                        }
                    }
                })
                .flow(1)
                .open();
        });
    }

    @Test(timeout = 20000)
    public void testReceiverAsyncSettleAfterReceivingMultipleMessages(TestContext context) {
        Async async = context.async();
        connect(context, connection -> {
            connection.open();
            AtomicInteger counter = new AtomicInteger(0);
            AtomicBoolean msgAccepted = new AtomicBoolean();
            ProtonReceiver receiver = connection.createReceiver(MockServer.Addresses.five_messages.toString());

            // Set the receiver not to auto-accept messages after the handler runs
            // and in turn not auto-settle, or replenish the credit used. We will
            // accept+settle the message manually at a later point.
            receiver.setAutoAccept(false);

            receiver.handler((d, m) -> {
                    int count = counter.incrementAndGet();
                    switch (count) {
                        case 1: // Fall-through
                        case 2: // Fall-through
                        case 3: {
                            validateMessage(context, count, String.valueOf(count), m);
                            break;
                        }
                        case 4: {
                            validateMessage(context, count, String.valueOf(count), m);

                            // We only issue 4 credits, so we should not get more
                            // messages until one is acked and a credit flowed, use
                            // the callback for this msg to do that
                            vertx.setTimer(1000, x -> {
                                LOG.trace("Settling msg 4 and flowing more credit");
                                accepted(d, true);
                                msgAccepted.set(true);
                            });

                            // Check that we haven't processed any more messages before then
                            vertx.setTimer(500, x -> {
                                LOG.trace("Checking msg 5 not received yet");
                                context.assertEquals(4, counter.get());
                            });
                            break;
                        }
                        case 5: {
                            validateMessage(context, count, String.valueOf(count), m);
                            context.assertTrue(msgAccepted.get(), "An earlier message was not yet accepted+settled,"
                                                                    + " should not have recieved message 5 yet!");

                            // Got the last message, lets finish the test.
                            LOG.trace("Got msg 5, completing async");
                            async.complete();
                            connection.disconnect();
                            break;
                        }
                    }
                })
                .flow(4)
                .open();
        });
    }

    @Test(timeout = 20000)
    public void testIsAnonymousRelaySupported(TestContext context) {
        Async async = context.async();
        connect(context, connection -> {
            context.assertFalse(connection.isAnonymousRelaySupported(),
                    "Connection not yet open, so result should be false");
            connection.openHandler(x -> {
                context.assertTrue(connection.isAnonymousRelaySupported(),
                        "Connection now open, server supports relay, should be true");

                connection.disconnect();
                async.complete();
            })
            .open();
        });
    }

    @Test(timeout = 20000)
    public void testAnonymousRelayIsNotSupported(TestContext context) {
        ((ProtonServerImpl) server.getProtonServer()).setAdvertiseAnonymousRelayCapability(false);
        Async async = context.async();
        connect(context, connection -> {
            context.assertFalse(connection.isAnonymousRelaySupported(),
                    "Connection not yet open, so result should be false");
            connection.openHandler(x -> {
                context.assertFalse(connection.isAnonymousRelaySupported(),
                        "Connection now open, server does not support relay, should be false");

                connection.disconnect();
                async.complete();
            })
            .open();
        });
    }

    @Test(timeout = 20000)
    public void testAnonymousSenderEnforcesMessageHasAddress(TestContext context) {
        Async async = context.async();
        connect(context, connection->{
            connection.open();
            ProtonSender sender = connection.createSender(null);
            Message messageWithNoAddress = Proton.message();
            try {
                sender.send(tag("t1"), messageWithNoAddress);
                context.fail("Send should have thrown IAE due to lack of message address");
            } catch (IllegalArgumentException iae) {
                // Expected
                connection.disconnect();
                async.complete();
            }
        });
    }

    @Test(timeout = 20000)
    public void testNonAnonymousSenderDoesNotEnforceMessageHasAddress(TestContext context) {
        Async async = context.async();
        connect(context, connection->{
            connection.open();
            ProtonSender sender = connection.createSender(MockServer.Addresses.drop.toString());
            Message messageWithNoAddress = Proton.message();
            sender.send(tag("t1"), messageWithNoAddress);
            connection.disconnect();
            async.complete();
        });
    }

    @Test(timeout = 20000)
    public void testDefaultAnonymousSenderSpecifiesLinkTarget(TestContext context) throws Exception {
        server.close();
        Async async = context.async();

        ProtonServer protonServer = null;
        try {
            protonServer = createServer((serverConnection) -> processConnectionAnonymousSenderSpecifiesLinkTarget(context, async, serverConnection));

            ProtonClient client = ProtonClient.create(vertx);
            client.connect("localhost", protonServer.actualPort(), res -> {
                context.assertTrue(res.succeeded());

                ProtonConnection connection =  res.result();
                connection.openHandler(x -> {
                    LOG.trace("Client connection opened");

                    ProtonSender sender = connection.createSender(null);
                    // Can optionally add an openHandler or sendQueueDrainHandler
                    // to await remote sender open completing or credit to send being
                    // granted. But here we will just buffer the send immediately.
                    sender.open();
                    sender.send(tag("tag"), message("ignored", "content"));
                })
                .open();
            });

            async.awaitSuccess();
        } finally {
            if (protonServer != null) {
                protonServer.close();
            }
        }
    }

    private void processConnectionAnonymousSenderSpecifiesLinkTarget(TestContext context, Async async, ProtonConnection serverConnection) {
        serverConnection.sessionOpenHandler(session -> session.open());
        serverConnection.receiverOpenHandler(receiver -> {
            LOG.trace("Server receiver opened");
            //TODO: set the local target on link before opening it
            receiver.handler((delivery, msg) -> {
                // We got the message that was sent, complete the test
                LOG.trace("Server got msg: {0}", getMessageBody(context, msg));
                serverConnection.disconnect();
                async.complete();
            });

            // Verify that the remote link target (set by the client) matches
            // up to the expected value to signal use of the anonymous relay
            Target remoteTarget = receiver.getRemoteTarget();
            context.assertNotNull(remoteTarget, "Client did not set a link target");
            context.assertNull(remoteTarget.getAddress(), "Unexpected target address");

            receiver.flow(10).open();
        });
        serverConnection.openHandler(result -> {
            serverConnection.open();
        });
    }

    @Test(timeout = 20000)
    public void testRemoteCloseDefaultSessionWithError(TestContext context) throws Exception {
        remoteCloseDefaultSessionTestImpl(context, true);
    }

    @Test(timeout = 20000)
    public void testRemoteCloseDefaultSessionWithoutError(TestContext context) throws Exception {
        remoteCloseDefaultSessionTestImpl(context, false);
    }

    private void remoteCloseDefaultSessionTestImpl(TestContext context, boolean sessionError)
            throws InterruptedException, ExecutionException {
        server.close();
        Async async = context.async();

        ProtonServer protonServer = null;
        try {
            protonServer = createServer(serverConnection -> {
                Future<ProtonSession> sessionFuture = Future.<ProtonSession>future();
                // Expect a session to open, when the sender is created by the client
                serverConnection.sessionOpenHandler(serverSession -> {
                    LOG.trace("Server session open");
                    serverSession.open();
                    sessionFuture.complete(serverSession);
                    });
                // Expect a receiver link, then close the session after opening it.
                serverConnection.receiverOpenHandler(serverReceiver -> {
                    LOG.trace("Server receiver open");
                    serverReceiver.flow(10).open();

                    context.assertTrue(sessionFuture.succeeded(), "Session future not [yet] succeeded");
                    LOG.trace("Server session close");
                    ProtonSession s = sessionFuture.result();
                    if (sessionError) {
                        ErrorCondition error = new ErrorCondition();
                        error.setCondition(AmqpError.INTERNAL_ERROR);
                        error.setDescription("error description");
                        s.setCondition(error);
                    }
                    s.close();
                });
                serverConnection.openHandler(result -> {
                    LOG.trace("Server connection open");
                    serverConnection.open();
                });
            });

            //===== Client Handling  =====

            ProtonClient client = ProtonClient.create(vertx);
            client.connect("localhost", protonServer.actualPort(), res -> {
                context.assertTrue(res.succeeded());

                ProtonConnection connection =  res.result();
                connection.openHandler(x -> {
                        context.assertTrue(x.succeeded(), "Connection open failed");
                        LOG.trace("Client connection opened");

                        // Create a sender to provoke creation (and subsequent
                        // closure of by the server) the connections default session
                        connection.createSender(null).open();
                    });
                connection.closeHandler(x -> {
                    LOG.trace("Connection close handler called (as espected): " + x.cause());
                    async.complete();
                   });
                connection.open();
            });

            async.awaitSuccess();
        } finally {
            if (protonServer != null) {
                protonServer.close();
            }
        }
    }

    @Test(timeout = 20000)
    public void testReceiverOpenWithAtLeastOnceQos(TestContext context) throws Exception {
        doOpenLinkWithQosTestImpl(context, true, ProtonQoS.AT_LEAST_ONCE);
    }

    @Test(timeout = 20000)
    public void testReceiverOpenWithAtMostOnceQos(TestContext context) throws Exception {
        doOpenLinkWithQosTestImpl(context, true, ProtonQoS.AT_MOST_ONCE);
    }

    @Test(timeout = 20000)
    public void testSenderOpenWithAtLeastOnceQos(TestContext context) throws Exception {
        doOpenLinkWithQosTestImpl(context, false, ProtonQoS.AT_LEAST_ONCE);
    }

    @Test(timeout = 20000)
    public void testSenderOpenWithAtMostOnceQos(TestContext context) throws Exception {
        doOpenLinkWithQosTestImpl(context, false, ProtonQoS.AT_MOST_ONCE);
    }

    public void doOpenLinkWithQosTestImpl(TestContext context, boolean clientSender, ProtonQoS qos) throws Exception {
        server.close();
        Async serverAsync = context.async();
        Async clientAsync = context.async();

        ProtonServer protonServer = null;
        try {
            protonServer = createServer((serverConnection) -> {
                serverConnection.openHandler(result -> {
                    serverConnection.open();
                });
                serverConnection.sessionOpenHandler(session -> session.open());
                if(clientSender) {
                    serverConnection.receiverOpenHandler(receiver -> {
                        context.assertEquals(qos, receiver.getRemoteQoS(), "unexpected remote qos value");
                        LOG.trace("Server receiver opened");
                        receiver.open();
                        serverAsync.complete();
                    });
                } else {
                    serverConnection.senderOpenHandler(sender -> {
                        context.assertEquals(qos, sender.getRemoteQoS(), "unexpected remote qos value");
                        LOG.trace("Server sender opened");
                        sender.open();
                        serverAsync.complete();
                    });
                }
            });

            //===== Client Handling  =====

            ProtonClient client = ProtonClient.create(vertx);
            client.connect("localhost", protonServer.actualPort(), res -> {
                context.assertTrue(res.succeeded());

                ProtonConnection connection =  res.result();
                connection.openHandler(x -> {
                    LOG.trace("Client connection opened");
                    final ProtonLink<?> link;
                    if(clientSender) {
                        link = connection.createSender(null);
                    } else {
                        link = connection.createReceiver("some-address");
                    }
                    link.setQoS(qos);

                    link.openHandler(y -> {
                        LOG.trace("Client link opened");
                        context.assertEquals(qos, link.getRemoteQoS(), "unexpected remote qos value");
                        clientAsync.complete();
                    });
                    link.open();

                })
                .open();
            });

            serverAsync.awaitSuccess();
            clientAsync.awaitSuccess();
        } finally {
            if (protonServer != null) {
                protonServer.close();
            }
        }
    }

    private ProtonServer createServer(Handler<ProtonConnection> serverConnHandler) throws InterruptedException, ExecutionException {
        ProtonServer server = ProtonServer.create(vertx);

        server.connectHandler(serverConnHandler);

        FutureHandler<ProtonServer, AsyncResult<ProtonServer>> handler = FutureHandler.asyncResult();
        server.listen(0, handler);
        handler.get();

        return server;
    }

    private void validateMessage(TestContext context, int count, Object expected, Message msg) {
        Object actual = getMessageBody(context, msg);
        LOG.trace("Got msg {0}, body: {1}", count, actual);

        context.assertEquals(expected, actual, "Unexpected message body");
    }

    private Object getMessageBody(TestContext context, Message msg) {
        Section body = msg.getBody();

        context.assertNotNull(body);
        context.assertTrue(body instanceof AmqpValue);

        return ((AmqpValue) body).getValue();
    }
}
