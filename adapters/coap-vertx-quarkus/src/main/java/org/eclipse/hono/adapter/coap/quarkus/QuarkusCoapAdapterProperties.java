/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.adapter.coap.quarkus;

import org.eclipse.hono.adapter.coap.CoapAdapterProperties;

import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.arc.config.ConfigProperties.NamingStrategy;

/**
 * Configuration properties for the CoAP adapter.
 */
@ConfigProperties(prefix = "hono.coap", namingStrategy = NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class QuarkusCoapAdapterProperties extends CoapAdapterProperties {
}
