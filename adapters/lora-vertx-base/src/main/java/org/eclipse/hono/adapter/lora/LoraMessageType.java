/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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
 * An enum defining different types of messages exchanged with LoRa providers.
 */
public enum LoraMessageType {
    JOIN, UPLINK, DOWNLINK, UNKNOWN
}
