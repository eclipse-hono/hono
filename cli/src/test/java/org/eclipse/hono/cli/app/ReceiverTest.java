///*******************************************************************************
// * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
// *
// * See the NOTICE file(s) distributed with this work for additional
// * information regarding copyright ownership.
// *
// * This program and the accompanying materials are made available under the
// * terms of the Eclipse Public License 2.0 which is available at
// * http://www.eclipse.org/legal/epl-2.0
// *
// * SPDX-License-Identifier: EPL-2.0
// *******************************************************************************/
//
//package org.eclipse.hono.cli.app;
//
//import io.vertx.junit5.VertxExtension;
//import io.vertx.junit5.VertxTestContext;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//
///**
// * Test cases verifying the behavior of {@code Receiver}.
// *
// */
//@ExtendWith(VertxExtension.class)
//public class ReceiverTest {
//
//    private Receiver receiver;
//
//    /**
//     * Sets up the receiver with mocks.
//     *
//     */
////    @SuppressWarnings("unchecked")
////    @BeforeEach
////    public void setup() {
////
////        final Vertx vertx = mock(Vertx.class);
////        when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));
////
////        final ApplicationClientFactory connection = mock(ApplicationClientFactory.class);
////        when(connection.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
////        when(connection.createTelemetryConsumer(anyString(), any(Consumer.class), any(Handler.class)))
////                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
////        when(connection.createEventConsumer(anyString(), any(Consumer.class), any(Handler.class)))
////                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
////
////        receiver = new Receiver();
////        receiver.setApplicationClientFactory(connection);
////        receiver.setVertx(vertx);
////        receiver.tenantId = "TEST_TENANT";
////    }
//
//    /**
//     * Verifies that the receiver is started successfully with message.type=telemetry.
//     *
//     * @param context The vert.x test context.
//     */
//    @Test
//    public void testTelemetryStart(final VertxTestContext context) {
//        receiver.messageType = "telemetry";
//
////        receiver.start().setHandler(
////                context.succeeding(result ->{
////                   context.verify(()->{
////                       assertNotNull(result.list());
////                       assertEquals(result.size(), 1);
////                   });
////                    context.completeNow();
////                }));
//    }
//
//    /**
//     * Verifies that the receiver is started successfully with message.type=event.
//     *
//     * @param context The vert.x test context.
//     */
//    @Test
//    public void testEventStart(final VertxTestContext context) {
//        receiver.messageType = "event";
//        receiver.start().setHandler(
//                context.succeeding(result -> {
//                    context.verify(() -> {
//                        assertNotNull(result.list());
//                        assertEquals(result.size(), 1);
//                    });
//                    context.completeNow();
//                }));
//    }
//
//    /**
//     * Verifies that the receiver is started successfully with message.type=all.
//     *
//     * @param context The vert.x test context.
//     */
//    @Test
//    public void testDefaultStart(final VertxTestContext context) {
//        receiver.messageType="all";
//
//        receiver.start().setHandler(
//                context.succeeding(result -> {
//                    context.verify(() -> {
//                        assertNotNull(result.list());
//                        assertEquals(result.size(), 2);
//                    });
//                    context.completeNow();
//                }));
//    }
//
//    /**
//     * Verifies that the receiver fails to start when invalid value is passed to message.type.
//     *
//     * @param context The vert.x test context.
//     */
//    @Test
//    public void testInvalidTypeStart(final VertxTestContext context) {
//        receiver.messageType = "xxxxx";
//        receiver.start().setHandler(
//                context.failing(result -> context.completeNow()));
//    }
//}
