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

package org.eclipse.hono.deviceregistry;

import java.io.IOException;
import com.google.common.io.Resources;

import io.vertx.core.buffer.Buffer;

/**
 * Utility methods for testing functionality around credentials and tenant management.
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

        final var buffer = Resources.toByteArray(Resources.getResource(DeviceRegistryTestUtils.class, resourceName));
        return Buffer.buffer(buffer);

    }
}
