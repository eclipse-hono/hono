/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the behavior of {@link MessageAnnotationsInjectAdapter} and {@link MessageAnnotationsExtractAdapter}.
 *
 */
public class MessageAnnotationsInjectExtractAdapterTest {

    /**
     * Verifies that the same entries injected via the {@code MessageAnnotationsInjectAdapter} are extracted via the
     * {@code MessageAnnotationsExtractAdapter}.
     * Also verifies that there are no errors during encoding/decoding of the message with the injected entries.
     */
    @Test
    public void testInjectAndExtract() {
        final String propertiesMapName = "map";
        final Map<String, String> testEntries = new HashMap<>();
        testEntries.put("key1", "value1");
        testEntries.put("key2", "value2");

        final Message message = new MessageImpl();
        // inject the properties
        final MessageAnnotationsInjectAdapter injectAdapter = new MessageAnnotationsInjectAdapter(message, propertiesMapName);
        testEntries.forEach((key, value) -> {
            injectAdapter.put(key, value);
        });

        // encode the message
        final WritableBuffer.ByteBufferWrapper buffer = WritableBuffer.ByteBufferWrapper.allocate(100);
        message.encode(buffer);

        // decode the message
        final Message decodedMessage = new MessageImpl();
        decodedMessage.decode(buffer.toReadableBuffer());
        // extract the properties from the decoded message
        final MessageAnnotationsExtractAdapter extractAdapter = new MessageAnnotationsExtractAdapter(decodedMessage, propertiesMapName);
        extractAdapter.iterator().forEachRemaining(extractedEntry -> {
            assertThat(extractedEntry.getValue()).isEqualTo(testEntries.get(extractedEntry.getKey()));
        });
    }
}
