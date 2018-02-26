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
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.util.Constants;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A helper class for integration tests.
 *
 */
public final class IntegrationTestSupport {

    public static final String DEFAULT_HOST = InetAddress.getLoopbackAddress().getHostAddress();
    public static final int    DEFAULT_DOWNSTREAM_PORT = 15672;
    public static final int    DEFAULT_DEVICEREGISTRY_AMQP_PORT = 25672;
    public static final int    DEFAULT_DEVICEREGISTRY_HTTP_PORT = 28080;

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
    public static final String PROPERTY_TENANT = "tenant";

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

    public static final String PATH_SEPARATOR = System.getProperty("hono.pathSeparator", "/");
    public static final int    MSG_COUNT = Integer.getInteger("msg.count", 1000);

    private IntegrationTestSupport() {
        // prevent instantiation
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
}
