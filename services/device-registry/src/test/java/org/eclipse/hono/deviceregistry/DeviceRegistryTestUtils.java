/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import java.io.IOException;
import java.io.InputStream;

import io.vertx.core.buffer.Buffer;

/**
 * Utility methods for testing functionality around credentials management.
 *
 */
public final class DeviceRegistryTestUtils {

    private DeviceRegistryTestUtils() {
        // prevent instantiation
    }

    /**
     * Reads the contents from a file using this class' class loader.
     * 
     * @param resourceName The name of the resource to load.
     * @return The contents of the file.
     * @throws IOException if the file cannot be read.
     */
    public static Buffer readFile(final String resourceName) throws IOException {

        Buffer result = Buffer.buffer();
        try (InputStream is = DeviceRegistryTestUtils.class.getResourceAsStream(resourceName)) {
            int bytesRead = 0;
            byte[] readBuffer = new byte[4096];
            while ((bytesRead = is.read(readBuffer)) != -1) {
                result.appendBytes(readBuffer, 0, bytesRead);
            }
        }
        return result;
    }
}
