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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

/**
 * Example base class for sending messages to Hono and signal the readiness for commands for a certain time interval.
 * <p>
 * This class simulates an HTTP device. It sends different kind of messages (defined per enum in an array) and sends them sequentially
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

    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Different types of requests this application may send.
     */
    private enum Request {
        EmptyEventWithTtd(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 60, true),
        EmptyEventAlreadyInvalid(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 0, true),
        JsonEventWithoutTtd(new JsonObject().put("threshold", "exceeded"), "application/json", null, true),
        JsonEventWithTtd(new JsonObject().put("threshold", "exceeded"), "application/json", 120, true),
        TelemetryWithoutTtd(new JsonObject().put("wheather", "sunny"), "application/json", null, false),
        TelemetryWithTtd(new JsonObject().put("wheather", "cloudy"), "application/json", 60, false);

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

        Request(final JsonObject payload, final String contentType, final Integer ttd, final Boolean isEvent) {
            this.payload = payload;
            this.contentType = contentType;
            this.ttd = ttd;
            this.isEvent = isEvent;
        }

        public MultiMap getHeaders() {

            final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            if (ttd != null) {
                headers.add(Constants.HEADER_TIME_TIL_DISCONNECT, ttd.toString());
            }
            return headers;
        }

        @Override
        public String toString() {
            return String.format("%s [content type: %s, ttd: %d, is event: %b, payload: %s]",
                    name(), contentType, ttd, isEvent, payload);
        }
    }

    private final List<Request> requests = Arrays.asList(
            Request.EmptyEventWithTtd,
            Request.EmptyEventWithTtd,
            Request.EmptyEventWithTtd,
            Request.TelemetryWithoutTtd,
            Request.EmptyEventWithTtd,
            Request.TelemetryWithoutTtd,
            Request.JsonEventWithTtd,
            Request.TelemetryWithoutTtd,
            Request.JsonEventWithoutTtd,
            Request.EmptyEventAlreadyInvalid,
            Request.TelemetryWithTtd,
            Request.TelemetryWithoutTtd
    );

    private final HttpClient httpClient;
    private final MultiMap standardRequestHeaders;

    private HonoHttpDevice() {
        final HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(HONO_HTTP_ADAPTER_HOST);
        options.setDefaultPort(HONO_HTTP_ADAPTER_PORT);
        httpClient = VERTX.createHttpClient(options);
        standardRequestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(TENANT_ID, DEVICE_AUTH_ID, DEVICE_PASSWORD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);
    }

    public static void main(final String[] args) {
        final HonoHttpDevice httpDevice = new HonoHttpDevice();
        httpDevice.sendData();

        // give some time for flushing asynchronous message buffers before shutdown
        VERTX.setTimer(2000, timerId -> {
            VERTX.close();
        });
    }

    /**
     * Send a message to Hono HTTP adapter. Delay any successful response by 1000 milliseconds.
     * 
     * @param request The request to send.
     * @return The HTTP response headers.
     */
    private CompletableFuture<Void> sendMessage(final Request request) {

        final CompletableFuture<Void> result = new CompletableFuture<>();

        final Predicate<Integer> successfulStatus = statusCode -> statusCode >= 200 && statusCode < 300;

        final HttpClientRequest req = httpClient
                .post(request.isEvent ? "/event" : "/telemetry")
                .handler(response -> {
                    System.out.println(response.statusCode() + " " + response.statusMessage());
                    if (successfulStatus.test(response.statusCode())) {
                        final MultiMap resultMap = response.headers();
                        resultMap.entries().stream().forEach(entry -> {
                            System.out.println(String.format("%s: %s", entry.getKey(), entry.getValue()));
                        });
                        response.bodyHandler(b -> {
                            if (b.length() > 0) {
                                System.out.println();
                                System.out.println(b.toString());
                            }
                        });
                        final String commandReqId = response.headers().get(Constants.HEADER_COMMAND_REQUEST_ID);
                        if (commandReqId == null) {
                            VERTX.setTimer(1000, l -> result.complete(null));
                        } else {
                            // response contains a command
                            sendCommandResponse("text/plain", Buffer.buffer("ok"), commandReqId, HttpURLConnection.HTTP_OK)
                            .map(status -> {
                                System.out.println(String.format("sent response to command [HTTP response status: %d]", status));
                                VERTX.setTimer(1000, l -> result.complete(null));
                                return null;
                            });
                        }
                    } else {
                        result.completeExceptionally(new ServiceInvocationException(response.statusCode()));
                    }
                }).exceptionHandler(t -> result.completeExceptionally(t));

        req.headers().addAll(standardRequestHeaders);
        req.headers().addAll(request.getHeaders());


        if (request.payload == null) {
            req.end();
        } else {
            req.end(request.payload.encode());
        }
        return result;
    }

    private Future<Integer> sendCommandResponse(final String contentType, final Buffer payload, final String commandReqId, final int status) {

        final Future<Integer> result = Future.future();
        final HttpClientRequest req = httpClient.post(String.format("/control/res/%s", commandReqId, status))
                .handler(response -> {
                    result.complete(response.statusCode());
                });
        req.putHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS, Integer.toString(status));
        req.headers().addAll(standardRequestHeaders);
        if (payload == null) {
            req.end();
        } else {
            req.end(payload);
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
        System.out.println(String.format("Total number of requests to send: %s", requests.size()));


        requests.stream().forEachOrdered(request -> {

            System.out.println();
            System.out.println();
            System.out.println(String.format("Sending request [type: %s] ...", request.toString()));

            final CompletableFuture<Void> responseFuture = sendMessage(request);

            try {
                responseFuture.join();
            } catch (final CompletionException e) {
                System.err.println("error sending request to HTTP adapter: " + e.getMessage());
            }
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
