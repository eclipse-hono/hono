/*******************************************************************************
O * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

/**
 * Example base class for sending messages to Hono and signal the readiness for commands for a certain time interval.
 * <p>
 * This class simulates an HTTP device. It sends different kind of messages (defined per enum in an array) and sends them sequentially
 * with 1 second delay. Some messages signal the command readiness while others do not - see the enum description for details.
 */
public class HonoHttpDevice {

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
     Define the host where Hono's HTTP adapter can be reached.
     */
    public static final String HONO_HTTP_ADAPTER_HOST = "localhost";
    /**
     * Port of Hono's http adapter microservice.
     */
    public static final int HONO_HTTP_ADAPTER_PORT = 8080;
    /**
     * The tenant ID to use for these examples.
     */
    public static final String TENANT_ID = "DEFAULT_TENANT";

    /**
     * The CORS <em>origin</em> address to use for sending messages.
     */
    protected static final String ORIGIN_URI = "http://hono.eclipse.org";

    private static final Vertx VERTX = Vertx.vertx();
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    /**
     * Different types of request messages this application may send.
     */
    private enum Request {
        EMPTY_EVENT_WITH_TTD(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 60, true),
        EMPTY_EVENT_ALREADY_INVALID(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, 0, true),
        JSON_EVENT_WITHOUT_TTD(new JsonObject().put("threshold", "exceeded"), CONTENT_TYPE_APPLICATION_JSON, null, true),
        JSON_EVENT_WITH_TTD(new JsonObject().put("threshold", "exceeded"), CONTENT_TYPE_APPLICATION_JSON, 120, true),
        TELEMETRY_WITHOUT_TTD(new JsonObject().put("weather", "sunny"), CONTENT_TYPE_APPLICATION_JSON, null, false),
        TELEMETRY_WITH_TTD(new JsonObject().put("weather", "cloudy"), CONTENT_TYPE_APPLICATION_JSON, 60, false);

        private final String payload;
        private final String contentType;
        private final Integer ttd;
        private final boolean isEvent;

        /**
         * Creates a new request message.
         *
         * @param payload The JSON payload of the message or {@code null} if the message has no payload.
         * @param contentType The type of the payload or {@code null} if unknown.
         * @param ttd The number of seconds after which the client will disconnect, once the request has been sent.
         *            May be {@code null}.
         * @param isEvent {@code true} if the message represents an <em>event</em> rather than <em>telemetry</em> data.
         */
        Request(final JsonObject payload, final String contentType, final Integer ttd, final boolean isEvent) {
            this.payload = Optional.ofNullable(payload).map(JsonObject::encode).orElse(null);
            this.contentType = contentType;
            this.ttd = ttd;
            this.isEvent = isEvent;
        }

        public MultiMap getHeaders() {

            final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            if (ttd != null) {
                headers.add(Constants.HEADER_TIME_TILL_DISCONNECT, ttd.toString());
            }
            return headers;
        }

        @Override
        public String toString() {
            return String.format("%s [content type: %s, ttd: %d, is event: %b, payload: %s]",
                    name(), contentType, ttd, isEvent, payload);
        }
    }

    private final List<Request> requests = List.of(
            Request.EMPTY_EVENT_WITH_TTD,
            Request.EMPTY_EVENT_WITH_TTD,
            Request.EMPTY_EVENT_WITH_TTD,
            Request.TELEMETRY_WITHOUT_TTD,
            Request.EMPTY_EVENT_WITH_TTD,
            Request.TELEMETRY_WITHOUT_TTD,
            Request.JSON_EVENT_WITH_TTD,
            Request.TELEMETRY_WITHOUT_TTD,
            Request.JSON_EVENT_WITHOUT_TTD,
            Request.EMPTY_EVENT_ALREADY_INVALID,
            Request.TELEMETRY_WITH_TTD,
            Request.TELEMETRY_WITHOUT_TTD
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

    /**
     * Sends some data.
     *
     * @param args The command line arguments (not used).
     */
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

        httpClient.request(HttpMethod.POST, request.isEvent ? "/event" : "/telemetry")
            .map(httpRequest -> {
                httpRequest.headers()
                    .addAll(standardRequestHeaders)
                    .addAll(request.getHeaders());
                return httpRequest;
            })
            .compose(httpRequest -> httpRequest.send(Optional.ofNullable(request.payload)
                    .map(Buffer::buffer)
                    .orElseGet(Buffer::buffer)))
                .onFailure(result::completeExceptionally)
                .onSuccess(response -> {
                    System.out.println(response.statusCode() + " " + response.statusMessage());
                    if (StatusCodeMapper.isSuccessful(response.statusCode())) {
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
                            sendCommandResponse(commandReqId, HttpURLConnection.HTTP_OK, Buffer.buffer("ok"), "text/plain")
                            .map(status -> {
                                System.out.println(String.format("sent response to command [HTTP response status: %d]", status));
                                VERTX.setTimer(1000, l -> result.complete(null));
                                return null;
                            });
                        }
                    } else {
                        result.completeExceptionally(StatusCodeMapper.from(response.statusCode(), response.statusMessage()));
                    }
                });

        return result;
    }

    private Future<Integer> sendCommandResponse(
            final String commandReqId,
            final int status,
            final Buffer payload,
            final String contentType) {

        return httpClient.request(HttpMethod.POST, String.format("/%s/res/%s", CommandConstants.COMMAND_ENDPOINT, commandReqId))
                .map(httpRequest -> {
                    httpRequest.putHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS, Integer.toString(status));
                    Optional.ofNullable(contentType)
                        .ifPresent(ct -> httpRequest.putHeader(HttpHeaders.CONTENT_TYPE, ct));
                    httpRequest.headers().addAll(standardRequestHeaders);
                    return httpRequest;
                })
                .compose(httpRequest -> payload == null ? httpRequest.send() : httpRequest.send(payload))
                .map(HttpClientResponse::statusCode);
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
