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
package org.eclipse.hono;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.impl.ProtonSenderWriteStream;
import org.eclipse.hono.mom.rabbitmq.RabbitMqHelper;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryAdapter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Integration tests for Hono Server.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoServerTest {

    private static final Logger    LOG                     = LoggerFactory.getLogger(HonoServerTest.class);
    static Vertx                   vertx;
    static HonoServer           server;
    Random                         rnd;
    RabbitMqHelper                 rabbitMq;
    ProtonConnection               connection;
    ProtonSender                   protonSender;

    @BeforeClass
    public static void init(final TestContext ctx) {

        // Create the Vert.x instance
        vertx = Vertx.vertx();
        server = new HonoServer();
        server.setPort(0);
        server.setHost("0.0.0.0");

        vertx.deployVerticle(server, ctx.asyncAssertSuccess());
        vertx.deployVerticle(MessageDiscardingTelemetryAdapter.class.getName(), ctx.asyncAssertSuccess());
    }

    @Before
    public void setup(final TestContext ctx) throws Exception {

        Async connectionOpened = ctx.async();
        String host = InetAddress.getLoopbackAddress().getHostAddress();
        int port = server.getPort();
        // Create the Vert.x AMQP client instance
        TestSupport.connect(ctx, vertx, host, port, connectedCon -> {
            LOG.debug("client connected to server, now opening connection");
            connectedCon.openHandler(result -> {
                ctx.assertTrue(result.succeeded());
                ProtonConnection openedCon = result.result();
                LOG.debug("connection to [container: {}] open", openedCon.getRemoteContainer());
                connection = openedCon;
                connectionOpened.complete();
            }).open();
        });
    }

    @After
    public void disconnect(final TestContext ctx) {
        connection.close();
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) throws IOException {
        vertx.close(ctx.asyncAssertSuccess(done -> LOG.info("Vertx has been shut down")));
    }

    @Test
    public void testTelemetryUpload(final TestContext ctx) {
        LOG.debug("starting telemetry upload test");
        final int messagesToBeSent = 100;
        final Async sent = ctx.async(messagesToBeSent);
        int timeout = 2000; // milliseconds
        final AtomicLong start = new AtomicLong();

        protonSender = connection.createSender(HonoServer.NODE_ADDRESS_TELEMETRY_UPLOAD);
        protonSender
                .setQoS(ProtonQoS.AT_MOST_ONCE)
                .openHandler(senderOpen -> {
                    ctx.assertTrue(senderOpen.succeeded());
                    LOG.debug("outbound link created, now starting to send messages");
                    start.set(System.currentTimeMillis());
                    uploadTelemetryData(messagesToBeSent, protonSender, allProduced -> {
                        LOG.debug("all {} telemetry messages have been produced", messagesToBeSent);
                    }, sent);
                }).open();

        sent.await(timeout);
        LOG.info("messages ack'd after {} milliseconds: {}", System.currentTimeMillis() - start.get(),
                messagesToBeSent - sent.count());
        protonSender.close();
    }

    private void uploadTelemetryData(final int count, final ProtonSender sender, final Handler<Void> allProducedHandler,
            final Async sentMessages) {
        vertx.executeBlocking(future -> {
            ReadStream<Message> rs = new TelemetryDataReadStream(vertx, count);
            WriteStream<Message> ws = new ProtonSenderWriteStream(sender, delivered -> sentMessages.countDown());
            rs.endHandler(done -> future.complete());
            LOG.debug("pumping test telemetry data to Hono server");
            Pump.pump(rs, ws).start();
        }, done -> allProducedHandler.handle(null));
    }
}
