/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.hono.adapter.lora.LoraCommand;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.eclipse.hono.util.CommandEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

/**
 * Base class for implementing tests for {@link LoraProvider} implementations.
 *
 * @param <T> The type of provider to test.
 */
public abstract class LoraProviderTestBase<T extends LoraProvider> {
    static final String TEST_DEVICE_ID = "0102030405060708";

    /**
     * The provider under test.
     */
    protected T provider;

    /**
     * Creates a new instance of the provider under test.
     *
     * @return The instance to run tests against.
     */
    protected abstract T newProvider();

    /**
     * Creates a routing context representing a request from a provider's network server.
     *
     * @param type The type of message to include in the request.
     * @param classifiers The classifiers to use for loading the request message from the file system.
     * @return The routing context.
     * @throws IOException If the file containing the example message could not be loaded.
     */
    protected final RoutingContext getRequestContext(final LoraMessageType type, final String... classifiers) throws IOException {

        final Buffer message = LoraTestUtil.loadTestFile(provider.getProviderName(), LoraMessageType.UPLINK, classifiers);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        final RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);
        final RequestBody body = mock(RequestBody.class);
        when(body.buffer()).thenReturn(message);
        when(routingContext.body()).thenReturn(body);
        return routingContext;
    }

    /**
     * Sets up the fixture.
     *
     * @throws Exception if the example message file(s) cannot be read.
     */
    @BeforeEach
    public void setUp() throws Exception {
        provider = newProvider();
    }

    /**
     * Verifies that uplink messages are parsed correctly.
     *
     * @throws IOException If the file containing the example message could not be loaded.
     */
    @Test
    public void testGetMessageSucceedsForUplinkMessage() throws IOException {

        final RoutingContext request = getRequestContext(LoraMessageType.UPLINK);
        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(request);
        assertCommonUplinkProperties(loraMessage);
        assertMetaDataForUplinkMessage(loraMessage);
    }

    /**
     * Asserts presence of common properties in an uplink message.
     *
     * @param uplinkMessage The message to assert.
     */
    protected void assertCommonUplinkProperties(final UplinkLoraMessage uplinkMessage) {
        assertThat(uplinkMessage.getDevEUIAsString()).isEqualTo("01020304050607AB");
        assertThat(uplinkMessage.getPayload().getBytes()).isEqualTo("bumlux".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Asserts presence of meta data in an uplink message.
     * <p>
     * This method is invoked as part of the {@link #testGetMessageSucceedsForUplinkMessage()} test.
     * Subclasses should override this method in order to verify properties that are supported
     * by the particular provider.
     * <p>
     * This default implementation does nothing.
     *
     * @param uplinkMessage The message to assert.
     * @throws AssertionError if any property fails assertion.
     */
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage uplinkMessage) {
        // do nothing
    }

    /**
     * Verifies that command messages are formatted correctly.
     */
    @Test
    public void testGenerateCommandMessage() {
        final CommandEndpoint commandEndpoint = new CommandEndpoint();
        commandEndpoint.setPayloadProperties(Map.of("py-property", "my-property-value"));
        commandEndpoint.setUri("https://my-lns.io/{{deviceId}}/command");
        final LoraCommand command = provider.getCommand(commandEndpoint, TEST_DEVICE_ID,
            Buffer.buffer("bumlux".getBytes(StandardCharsets.UTF_8)), "2");
        assertThat(command.getUri()).isEqualTo("https://my-lns.io/" + TEST_DEVICE_ID + "/command");
        assertCommandFormat(command.getPayload());
    }

    /**
     * Asserts format of a lora command.
     * <p>
     * This method is invoked as part of the {@link #testGenerateCommandMessage()} test.
     * Subclasses should override this method in order to verify the format specified by the LNS.
     * <p>
     * This default implementation validates the default format.
     *
     * @param command The command to assert.
     * @throws AssertionError if any property fails assertion.
     */
    protected void assertCommandFormat(final JsonObject command) {
        assertThat(command.containsKey("payload")).isTrue();
        assertThat(command.getValue("payload")).isEqualTo("62756D6C7578");
    }
}
