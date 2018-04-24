/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.devices;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import org.eclipse.hono.util.NotificationConstants;
import org.eclipse.hono.util.NotificationDeviceCommandReadyConstants;

/**
 * Example base class for sending messages to Hono and signal the readiness for commands for a certain time interval.
 * <p>
 * This class implements all necessary code to simulate an http device. It sends event messages 20 times
 * in sequence and shows the necessary programming patterns for that. For this period, the device signals its readiness
 * to receive commands by using the specifically defined event for that purpose.
 * <p>
 */
public class HonoHttpDevice {

    /**
     * The number of messages to send.
     */
    public static final int COUNT = 10;
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

    private final Vertx vertx = Vertx.vertx();

    public static void main(final String[] args) {
        HonoHttpDevice httpDevice = new HonoHttpDevice();
        httpDevice.sendData();
    }

    /**
     * Send an event to Hono HTTP adapter. Delay the successful response by 1000 milliseconds.
     *
     * @param payload JSON object that will be sent as UTF-8 encoded String.
     * @param contentType The content-type that will be set for the event.
     * @return CompletableFuture&lt;MultiMap&gt; A completable future that contains the HTTP response in a MultiMap.
     */
    private CompletableFuture<MultiMap> sendEvent(final JsonObject payload, final String contentType) {
        final CompletableFuture<MultiMap> result = new CompletableFuture<>();

        final Predicate<Integer> successfulStatus = statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED;
        final HttpClientRequest req = vertx.createHttpClient()
                .post(HONO_HTTP_ADAPTER_PORT, HONO_HTTP_ADAPTER_HOST, "/event")
                .handler(response -> {
                    if (successfulStatus.test(response.statusCode())) {
                        vertx.setTimer(1000, l -> result.complete(response.headers()));
                    } else {
                        result.completeExceptionally(new ServiceInvocationException(response.statusCode()));
                    }
                }).exceptionHandler(t -> result.completeExceptionally(t));

        req.headers().addAll(MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, contentType)
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
     * Send events to Hono HTTP adapter {@link HonoHttpDevice#COUNT} times in a sequence.
     * <p>
     * Alternate the event every second time to be a device notification.
     */
    protected void sendData() {
            // then send single messages sequentially in a loop
            for (int messagesSent = 0; messagesSent < COUNT; messagesSent++) {
                // send message and wait until it was delivered
                String contentType;
                JsonObject eventJson;
                if (messagesSent %2 == 0) {
                    contentType = NotificationConstants.CONTENT_TYPE_DEVICE_COMMAND_READINESS_NOTIFICATION;
                    final long timeToBeReady = (messagesSent % 4 == 0) ? 1 : 60000;
                    eventJson = NotificationDeviceCommandReadyConstants.createDeviceCommandReadinessNotification(timeToBeReady);
                } else {
                    contentType = "application/json";
                    eventJson = new JsonObject().put("aKey", "aValue");
                }

                final CompletableFuture<MultiMap> eventResponseFuture = sendEvent(eventJson, contentType);

                try {
                    final MultiMap resultMap = eventResponseFuture.get();
                    System.out.println("Got #" + resultMap.size());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        // print a summary of the message deliveries.
        System.out.println("Total number of events: " + COUNT);

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
