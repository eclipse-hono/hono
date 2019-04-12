/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing messages to Hono.
 */
public abstract class AbstractDownstreamSender extends AbstractSender implements DownstreamSender {

    /**
     * A counter to be used for creating message IDs.
     */
    protected static final AtomicLong MESSAGE_COUNTER = new AtomicLong();

    private static final Pattern CHARSET_PATTERN = Pattern.compile("^.*;charset=(.*)$");

    /**
     * Creates a new sender.
     * 
     * @param connection The connection to use for interacting with the server.
     * @param sender The sender link to send messages over.
     * @param tenantId The identifier of the tenant that the
     *           devices belong to which have published the messages
     *           that this sender is used to send downstream.
     * @param targetAddress The target address to send the messages to.
     */
    protected AbstractDownstreamSender(
            final HonoConnection connection,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress) {

        super(connection, sender, tenantId, targetAddress);
    }


    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType) {
        return send(deviceId, null, payload, contentType);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final String payload, final String contentType) {
        return send(deviceId, null, payload, contentType);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);

        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        MessageHelper.setPayload(msg, contentType, payload);
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId);
        return send(msg);
    }

    private void addProperties(final Message msg, final String deviceId) {
        MessageHelper.addDeviceId(msg, deviceId);
    }

    private Charset getCharsetForContentType(final String contentType) {

        final Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.matches()) {
            return Charset.forName(m.group(1));
        } else {
            return StandardCharsets.UTF_8;
        }
    }
}
