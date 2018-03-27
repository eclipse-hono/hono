/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.tests;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Constants;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * A helper class for integration tests.
 *
 */
public final class IntegrationTestSupport {

    public static final String DEFAULT_HOST = InetAddress.getLoopbackAddress().getHostAddress();
    public static final int    DEFAULT_DOWNSTREAM_PORT = 15672;
    public static final int    DEFAULT_DEVICEREGISTRY_AMQP_PORT = 25672;
    public static final int    DEFAULT_DEVICEREGISTRY_HTTP_PORT = 28080;
    public static final int    DEFAULT_HTTP_PORT = 8080;
    public static final int    DEFAULT_MQTT_PORT = 1883;

    public static final String PROPERTY_AUTH_HOST = "auth.host";
    public static final String PROPERTY_AUTH_PORT = "auth.amqp.port";
    public static final String PROPERTY_HONO_HOST = "hono.host";
    public static final String PROPERTY_HONO_PORT = "hono.amqp.port";
    public static final String PROPERTY_HONO_USERNAME = "hono.username";
    public static final String PROPERTY_HONO_PASSWORD = "hono.password";
    public static final String PROPERTY_DEVICEREGISTRY_HOST = "deviceregistry.host";
    public static final String PROPERTY_DEVICEREGISTRY_AMQP_PORT = "deviceregistry.amqp.port";
    public static final String PROPERTY_DEVICEREGISTRY_HTTP_PORT = "deviceregistry.http.port";
    public static final String PROPERTY_DOWNSTREAM_HOST = "downstream.host";
    public static final String PROPERTY_DOWNSTREAM_PORT = "downstream.amqp.port";
    public static final String PROPERTY_DOWNSTREAM_USERNAME = "downstream.username";
    public static final String PROPERTY_DOWNSTREAM_PASSWORD = "downstream.password";
    public static final String PROPERTY_HTTP_HOST = "http.host";
    public static final String PROPERTY_HTTP_PORT = "http.port";
    public static final String PROPERTY_MQTT_HOST = "mqtt.host";
    public static final String PROPERTY_MQTT_PORT = "mqtt.port";
    public static final String PROPERTY_TENANT = "tenant";

    public static final String AUTH_HOST = System.getProperty(PROPERTY_AUTH_HOST, DEFAULT_HOST);
    public static final int    AUTH_PORT = Integer.getInteger(PROPERTY_AUTH_PORT, Constants.PORT_AMQP);

    public static final String HONO_HOST = System.getProperty(PROPERTY_HONO_HOST, DEFAULT_HOST);
    public static final int    HONO_PORT = Integer.getInteger(PROPERTY_HONO_PORT, Constants.PORT_AMQP);
    public static final String HONO_USER = System.getProperty(PROPERTY_HONO_USERNAME);
    public static final String HONO_PWD = System.getProperty(PROPERTY_HONO_PASSWORD);

    public static final String HONO_DEVICEREGISTRY_HOST = System.getProperty(PROPERTY_DEVICEREGISTRY_HOST, DEFAULT_HOST);
    public static final int    HONO_DEVICEREGISTRY_AMQP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_AMQP_PORT, DEFAULT_DEVICEREGISTRY_AMQP_PORT);
    public static final int    HONO_DEVICEREGISTRY_HTTP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_HTTP_PORT, DEFAULT_DEVICEREGISTRY_HTTP_PORT);

    public static final String DOWNSTREAM_HOST = System.getProperty(PROPERTY_DOWNSTREAM_HOST, DEFAULT_HOST);
    public static final int    DOWNSTREAM_PORT = Integer.getInteger(PROPERTY_DOWNSTREAM_PORT, DEFAULT_DOWNSTREAM_PORT);
    public static final String DOWNSTREAM_USER = System.getProperty(PROPERTY_DOWNSTREAM_USERNAME);
    public static final String DOWNSTREAM_PWD = System.getProperty(PROPERTY_DOWNSTREAM_PASSWORD);
    public static final String RESTRICTED_CONSUMER_NAME = "user1@HONO";
    public static final String RESTRICTED_CONSUMER_PWD = "pw";

    public static final String HTTP_HOST = System.getProperty(PROPERTY_HTTP_HOST, DEFAULT_HOST);
    public static final int    HTTP_PORT = Integer.getInteger(PROPERTY_HTTP_PORT, DEFAULT_HTTP_PORT);
    public static final String MQTT_HOST = System.getProperty(PROPERTY_MQTT_HOST, DEFAULT_HOST);
    public static final int    MQTT_PORT = Integer.getInteger(PROPERTY_MQTT_PORT, DEFAULT_MQTT_PORT);

    public static final String PATH_SEPARATOR = System.getProperty("hono.pathSeparator", "/");
    public static final int    MSG_COUNT = Integer.getInteger("msg.count", 1000);

    /**
     * The name of the tenant for which only the MQTT adapter is enabled.
     */
    public static final String TENANT_MQTT_ONLY = "MQTT_ONLY";
    /**
     * The name of the tenant for which only the HTTP adapter is enabled.
     */
    public static final String TENANT_HTTP_ONLY = "HTTP_ONLY";

    /**
     * A client for managing tenants/devices/credentials.
     */
    public DeviceRegistryHttpClient registry;
    /**
     * A client for connecting to the AMQP Messaging Network.
     */
    public HonoClient downstreamClient;

    private final List<String> tenantsToDelete = new LinkedList<>();
    private final Map<String, Set<String>> devicesToDelete = new HashMap<>();
    private final Vertx vertx;

    /**
     * Creates a new helper instance.
     * 
     * @param vertx The vert.x instance.
     */
    public IntegrationTestSupport(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Connects to the AMQP 1.0 Messaging Network.
     * 
     * @param ctx The vert.x test context.
     */
    public void init(final TestContext ctx) {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);

        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setUsername(IntegrationTestSupport.DOWNSTREAM_USER);
        downstreamProps.setPassword(IntegrationTestSupport.DOWNSTREAM_PWD);
        downstreamClient = new HonoClientImpl(vertx, downstreamProps);

        downstreamClient.connect().setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    public void deleteObjects(final TestContext ctx) {

        devicesToDelete.forEach((tenantId, devices) -> {
            devices.forEach(deviceId -> {
                final Async deletion = ctx.async();
                CompositeFuture.join(
                        registry.deregisterDevice(tenantId, deviceId),
                        registry.removeAllCredentials(tenantId, deviceId))
                    .setHandler(ok -> deletion.complete());
                deletion.await();
            });
        });
        devicesToDelete.clear();

        tenantsToDelete.forEach(tenantId -> {
            final Async deletion = ctx.async();
            registry.removeTenant(tenantId).setHandler(ok -> deletion.complete());
            deletion.await();
        });
        tenantsToDelete.clear();
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     * 
     * @param ctx The vert.x test context.
     */
    public void disconnect(final TestContext ctx) {

        downstreamClient.shutdown(ctx.asyncAssertSuccess());
    }

    /**
     * Gets a random tenant identifier and adds it to the list
     * of tenants to be deleted after the current test has finished.
     * 
     * @return The identifier.
     * @see #deleteObjects(TestContext)
     */
    public String getRandomTenantId() {
        final String tenantId = UUID.randomUUID().toString();
        tenantsToDelete.add(tenantId);
        return tenantId;
    }

    /**
     * Gets a random device identifier and adds it to the list
     * of devices to be deleted after the current test has finished.
     * 
     * @param tenantId The tenant that he device belongs to.
     * @return The identifier.
     * @see #deleteObjects(TestContext)
     */
    public String getRandomDeviceId(final String tenantId) {
        final String deviceId = UUID.randomUUID().toString();
        final Set<String> devices = devicesToDelete.computeIfAbsent(tenantId, t -> new HashSet<>());
        devices.add(deviceId);
        return deviceId;
    }

    /**
     * A simple implementation of subtree containment: all entries of the JsonObject that is tested to be contained
     * must be contained in the other JsonObject as well. Nested JsonObjects are treated the same by recursively calling
     * this method to test the containment.
     * JsonArrays are tested for containment as well: all elements in a JsonArray belonging to the contained JsonObject
     * must be present in the corresponding JsonArray of the other JsonObject as well. The sequence of the array elements
     * is not important (suitable for the current tests).
     * @param jsonObject The JsonObject that must fully contain the other JsonObject (but may contain more entries as well).
     * @param jsonObjectToBeContained The JsonObject that needs to be fully contained inside the other JsonObject.
     * @return The result of the containment test.
     */
    public static boolean testJsonObjectToBeContained(final JsonObject jsonObject, final JsonObject jsonObjectToBeContained) {
        if (jsonObjectToBeContained == null) {
            return true;
        }
        if (jsonObject == null) {
            return false;
        }
        AtomicBoolean containResult = new AtomicBoolean(true);

        jsonObjectToBeContained.forEach(entry -> {
            if (!jsonObject.containsKey(entry.getKey())) {
                containResult.set(false);
            } else {
                if (entry.getValue() == null) {
                    if (jsonObject.getValue(entry.getKey()) != null) {
                        containResult.set(false);
                    }
                } else if (entry.getValue() instanceof JsonObject) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonObject)) {
                        containResult.set(false);
                    } else {
                        if (!testJsonObjectToBeContained((JsonObject)entry.getValue(),
                                (JsonObject)jsonObject.getValue(entry.getKey()))) {
                            containResult.set(false);
                        }
                    }
                } else if (entry.getValue() instanceof JsonArray) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonArray)) {
                        containResult.set(false);
                    } else {
                        // compare two JsonArrays
                        JsonArray biggerArray = (JsonArray) jsonObject.getValue(entry.getKey());
                        JsonArray smallerArray = (JsonArray) entry.getValue();

                        if (!testJsonArrayToBeContained(biggerArray, smallerArray)) {
                            containResult.set(false);
                        }
                    }
                } else {
                    if (!(entry.getValue().equals(jsonObject.getValue(entry.getKey())))) {
                        containResult.set(false);
                    }
                }
            }
        });
        return containResult.get();
    }

    /**
     * A simple implementation of JsonArray containment: all elements of the JsonArray that is tested to be contained
     * must be contained in the other JsonArray as well. Contained JsonObjects are tested for subtree containment as
     * implemented in {@link #testJsonObjectToBeContained(JsonObject, JsonObject)}.
     * <p>
     * The order sequence of the elements is intentionally not important - the containing array is always iterated from
     * the beginning and the containment of an element is handled as successful if a suitable element in the containing
     * array was found (sufficient for the current tests).
     * <p>
     * For simplicity, the elements of the arrays must be of type JsonObject (sufficient for the current tests).
     * <p>
     * Also note that this implementation is by no means performance optimized - it is for sure not suitable for huge JsonArrays
     * (by using two nested iteration loops inside) and is meant only for quick test results on smaller JsonArrays.
     *
     * @param containingArray The JsonArray that must contain the elements of the other array (the sequence is not important).
     * @param containedArray The JsonArray that must consist only of elements that can be found in the containingArray
     *                       as well (by subtree containment test).
     * @return The result of the containment test.
     */
    public static boolean testJsonArrayToBeContained(final JsonArray containingArray, final JsonArray containedArray) {
        for (Object containedElem: containedArray) {
            // currently only support contained JsonObjects
            if (!(containedElem instanceof JsonObject)) {
                return false;
            }

            boolean containingElemFound = false;
            for (Object elemOfBiggerArray: containingArray) {
                if (!(elemOfBiggerArray instanceof JsonObject)) {
                    return false;
                }

                if (testJsonObjectToBeContained((JsonObject) elemOfBiggerArray, (JsonObject) containedElem)) {
                    containingElemFound = true;
                    break;
                }
            }
            if (!containingElemFound) {
                // a full iteration of the containing array did not find a matching element
                return false;
            }
        }
        return true;
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
    public static String getUsername(final String deviceId, final String tenant) {
        return String.format("%s@%s", deviceId, tenant);
    }
}
