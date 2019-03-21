/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

/**
 * Constants used in the lora adapter.
 */
public class LoraConstants {

    /**
     * The name of the AMQP 1.0 message application property containing the name of the LoRa protocol provider over
     * which an uploaded message has originally been received.
     */
    public static final String APP_PROPERTY_ORIG_LORA_PROVIDER = "orig_lora_provider";
    public static final String FIELD_PSK = "psk";
    public static final String FIELD_VIA = "via";
    public static final String FIELD_AUTH_ID = "auth-id";
    public static final String FIELD_LORA_CONFIG = "lora-network-server";
    public static final String FIELD_LORA_URL = "url";
    public static final String FIELD_LORA_PROVIDER = "provider";
    public static final String FIELD_LORA_VENDOR_PROPERTIES = "vendor-properties";
    public static final String FIELD_LORA_CREDENTIAL_IDENTITY = "identity";
    public static final String FIELD_LORA_CREDENTIAL_KEY = "key";
    public static final String FIELD_LORA_DEVICE_PORT = "lora-port";
    public static final String FIELD_LORA_DOWNLINK_PAYLOAD = "payload";
    public static final String EMPTY = "";

    private LoraConstants() {
        // prevent instantiation
    }
}
