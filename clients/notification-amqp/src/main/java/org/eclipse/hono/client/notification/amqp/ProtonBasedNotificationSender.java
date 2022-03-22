/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.notification.amqp;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.SenderCachingServiceClient;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * A vertx-proton based client for publishing notifications.
 */
public class ProtonBasedNotificationSender extends SenderCachingServiceClient implements NotificationSender {

    /**
     * Creates an instance.
     *
     * @param connection The connection to the AMQP 1.0 Messaging Network.
     * @throws NullPointerException if connection is {@code null}.
     */
    public ProtonBasedNotificationSender(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop(), false);
    }

    @Override
    public Future<Void> publish(final AbstractNotification notification) {
        Objects.requireNonNull(notification);
        return getOrCreateSenderLink(NotificationAddressHelper.getAddress(notification.getType()))
                .compose(sender -> sender.sendAndWaitForOutcome(createMessage(notification), NoopSpan.INSTANCE)
                        .onFailure(thr -> log.debug("error sending notification [{}]", notification, thr)))
                .mapEmpty();
    }

    private Message createMessage(final AbstractNotification notification) {
        final Message msg = ProtonHelper.message();
        final JsonObject value = JsonObject.mapFrom(notification);
        AmqpUtils.setJsonPayload(msg, value);
        msg.setAddress(NotificationAddressHelper.getAddress(notification.getType()));
        return msg;
    }

}
