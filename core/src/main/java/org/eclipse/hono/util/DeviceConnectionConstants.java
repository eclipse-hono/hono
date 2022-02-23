/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

/**
 * Constants used for dealing with the device connection data.
 * TODO These constants should be moved elsewhere.
 */
public final class DeviceConnectionConstants extends RequestResponseApiConstants {

    /**
     * The name of the field that contains the identifier of a gateway.
     */
    public static final String FIELD_GATEWAY_ID = "gateway-id";

    /**
     * The name of the optional field in the result of the <em>get last known gateway for device</em> operation
     * that contains the date when the last known gateway id was last updated.
     */
    public static final String FIELD_LAST_UPDATED = "last-updated";

    /**
     * The name of the field that contains the list of objects with protocol adapter instance id and device id.
     */
    public static final String FIELD_ADAPTER_INSTANCES = "adapter-instances";

    /**
     * The name of the field that contains the identifier of the protocol adapter instance.
     */
    public static final String FIELD_ADAPTER_INSTANCE_ID = "adapter-instance-id";

    /**
     * The name of the field that contains the array of gateway ids.
     */
    public static final String FIELD_GATEWAY_IDS = "gateway-ids";

    private DeviceConnectionConstants() {
        // prevent instantiation
    }
}
