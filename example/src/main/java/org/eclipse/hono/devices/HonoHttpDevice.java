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

package org.eclipse.hono.devices;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;

/**
 * Example base class for sending messages to Hono and signal the readiness for commands for a certain time interval.
 * <p>
 * This class simulates an http device. It sends different kind of messages (defined per enum in an array) and sends them sequentially
 * with 1 second delay. Some messages signal the command readiness while others do not - see the enum description for details.
 */
public class HonoHttpDevice {

    /**
     Define the host where Hono's HTTP adapter can be reached.
     */
    public static final String HONO_HTTP_ADAPTER_HOST = "localhost";

    /**
     * Port of Hono's http adapter microservice.
     */
    public static final int HONO_HTTP_ADAPTER_PORT = 8080;

    /**
     * The CORS <em>origin</em> address to use for sending messages.
     */
    protected static final String ORIGIN_URI = "http://hono.eclipse.org";

    /**
     * The tenant ID to use for these examples.
     */
    public static final String TENANT_ID = "DEFAULT_TENANT";

    /**
     * The authId of the device that is used inside this class.
     * NB: you need to register credentials for this authId before data can be sent.
     * Please refer to Hono's "Getting started" guide for details.
     */
    public static final String DEVICE_AUTH_ID = "sensor1";

    /**
     * The password to use for accessing the HTTP adapter.
     */
    public static final String DEVICE_PASSWORD = "hono-secret";

    /**
     * Different type of messages this application may sent, defined as enum.
     */
    private enum MessageTypes {
        EmptyEventWithTtd(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 60, true),
        EmptyEventAlreadyInvalid(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 0, true),
        JsonEventWithoutTtd(new JsonObject().put("anEventKey", "aValue"), "application/json", null, true),
        JsonEventWithTtd(new JsonObject().put("anEventKey", "aValue"), "application/json", 120, true),
        TelemetryWithoutTtd(new JsonObject().put("aTeleKey", "aValue"), "application/json", null, false),
        TelemetryWithTtd(new JsonObject().put("aTeleKey", "aValue"), "application/json", 60, false);

        /**
         * The payload of the message, defined as JsonObject. Maybe {@code null}.
         */
        private final JsonObject payload;
        /**
         * The Content-Type that is set for the message.
         */
        private final String contentType;
        /**
         * The <em>time-to-deliver</em> of the message, which is defined as application property of the AMQP 1.0 message.
         */
        private final Integer ttd;
        /**
         * Property to define if the message shall be sent as event or as telemetry message.
         */
        private final Boolean isEvent;

        MessageTypes(final JsonObject payload, final String contentType, final Integer ttd, final Boolean isEvent) {
            this.payload = payload;
            this.contentType = contentType;
            this.ttd = ttd;
            this.isEvent = isEvent;
        }

        @Override
        public String toString() {
            return "MessageTypes{" +
                    "payload=" + payload +
                    ", contentType='" + contentType + '\'' +
                    ", ttd=" + ttd +
                    ", isEvent=" + isEvent +
                    '}';
        }
    }

    final List<MessageTypes> messages = Arrays.asList(
            MessageTypes.EmptyEventWithTtd,
            MessageTypes.EmptyEventWithTtd,
            MessageTypes.EmptyEventWithTtd,
            MessageTypes.TelemetryWithoutTtd,
            MessageTypes.EmptyEventWithTtd,
            MessageTypes.TelemetryWithoutTtd,
            MessageTypes.JsonEventWithTtd,
            MessageTypes.TelemetryWithoutTtd,
            MessageTypes.JsonEventWithoutTtd,
            MessageTypes.EmptyEventAlreadyInvalid,
            MessageTypes.TelemetryWithTtd,
            MessageTypes.TelemetryWithoutTtd
    );

    private final Vertx vertx = Vertx.vertx();

    public static void main(final String[] args) {
        final HonoHttpDevice httpDevice = new HonoHttpDevice();
        httpDevice.sendData();
    }

    /**
     * Send a message to Hono HTTP adapter. Delay any successful response by 1000 milliseconds.
     *
     * @param payload JSON object that will be sent as UTF-8 encoded String.
     * @param headersToAdd A map that contains all headers to add to the http request.
     * @param asEvent If {@code true}, an event message is sent, otherwise a telemetry message.
     * @return CompletableFuture&lt;MultiMap&gt; A completable future that contains the HTTP response in a MultiMap.
     */
    private CompletableFuture<MultiMap> sendMessage(final JsonObject payload, final MultiMap headersToAdd, final boolean asEvent) {
        final CompletableFuture<MultiMap> result = new CompletableFuture<>();

        final Predicate<Integer> successfulStatus = statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED;
        final HttpClientRequest req = vertx.createHttpClient()
                .post(HONO_HTTP_ADAPTER_PORT, HONO_HTTP_ADAPTER_HOST, asEvent ? "/event" : "/telemetry")
                .handler(response -> {
                    if (successfulStatus.test(response.statusCode())) {
                        vertx.setTimer(1000, l -> result.complete(response.headers()));
                    } else {
                        result.completeExceptionally(new ServiceInvocationException(response.statusCode()));
                    }
                }).exceptionHandler(t -> result.completeExceptionally(t));

        req.headers().addAll(headersToAdd);
        req.headers().addAll(MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(TENANT_ID, DEVICE_AUTH_ID, DEVICE_PASSWORD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI));


        if (payload == null) {
            req.end();
        } else {
            req.end(payload.encode());
        }
        return result;
    }


    /**
     * Send messages to Hono HTTP adapter in a sequence.
     * <p>
     * Alternate the event every 4th time to be an event.
     */
    protected void sendData() {
        // Send single messages sequentially in a loop and print a summary of the message deliveries.
        System.out.println(String.format("Total number of messages: %s", messages.size()));


        messages.stream().forEachOrdered(messageType -> {
            final MultiMap headerMap = MultiMap.caseInsensitiveMultiMap();
            headerMap.add(HttpHeaders.CONTENT_TYPE, messageType.contentType);
            Optional.ofNullable(messageType.ttd).ifPresent(
                    timeToDeliver -> headerMap.add(Constants.HEADER_TIME_TIL_DISCONNECT, timeToDeliver.toString())
            );

            System.out.println(String.format("Sending message type %s", messageType.toString()));

            final CompletableFuture<MultiMap> responseFuture = sendMessage(messageType.payload, headerMap, messageType.isEvent);
            try {
                final MultiMap resultMap = responseFuture.get();
                System.out.println(String.format("Got %d response keys.", resultMap.size()));
                resultMap.entries().stream().forEach(entry -> {
                    System.out.println(String.format("  %s:%s", entry.getKey(), entry.getValue()));
                });
            } catch (final InterruptedException e) {
                e.printStackTrace();
            } catch (final ExecutionException e) {
                e.printStackTrace();
            }
        });

        // give some time for flushing asynchronous message buffers before shutdown
        vertx.setTimer(2000, timerId -> {
            vertx.close();
        });
    }

    /**
     * Creates an HTTP Basic auth header value for a device.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param password The device's password.
     * @return The header value.
     */
    private static String getBasicAuth(final String tenant, final String deviceId, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        final String username = getUsername(deviceId, tenant);
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(username).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

    /**
     * Creates an authentication identifier from a device and tenant ID.
     * <p>
     * The returned identifier can be used as the <em>username</em> with
     * Hono's protocol adapters that support username/password authentication.
     *
     * @param deviceId The device identifier.
     * @param tenant The tenant that the device belongs to.
     * @return The authentication identifier.
     */
    private static String getUsername(final String deviceId, final String tenant) {
        return String.format("%s@%s", deviceId, tenant);
    }

}
