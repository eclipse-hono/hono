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

package org.eclipse.hono.messaging;

import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * A default {@code SenderFactory} for creating {@code ProtonSender}s from a given connection.
 */
@Component
public class SenderFactoryImpl implements SenderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SenderFactoryImpl.class);
    private HonoMessagingConfigProperties config;

    /**
     * Sets the configuration properties.
     * 
     * @param config The configuration.
     * @throws NullPointerException if config is {@code null}.
     */
    @Autowired(required = false)
    public void setConfiguration(final HonoMessagingConfigProperties config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonSender> createSender(
            final ProtonConnection connection,
            final ResourceIdentifier address,
            final ProtonQoS qos,
            final Handler<ProtonSender> sendQueueDrainHandler,
            final Handler<Void> closeHook) {

        Objects.requireNonNull(connection);
        Objects.requireNonNull(address);
        Objects.requireNonNull(qos);

        if (connection.isDisconnected()) {
            return Future.failedFuture("connection is disconnected");
        } else {
            return newSession(connection, address).compose(session -> {
                return newSender(connection, session, address, qos, sendQueueDrainHandler, hook -> {
                    closeHook.handle(null);
                });
            });
        }
    }

    Future<ProtonSession> newSession(final ProtonConnection con, final ResourceIdentifier targetAddress) {
        final Future<ProtonSession> result = Future.future();
        ProtonSession session = con.attachments().get(targetAddress.getEndpoint(), ProtonSession.class);
        if (session != null) {
            LOG.debug("re-using existing session for sending {} data downstream", targetAddress.getEndpoint());
            result.complete(session);
        } else {
            LOG.debug("creating new session for sending {} data downstream", targetAddress.getEndpoint());
            session = con.createSession();
            session.openHandler(remoteOpen -> {
                if (remoteOpen.succeeded()) {
                    con.attachments().set(targetAddress.getEndpoint(), ProtonSession.class, remoteOpen.result());
                    result.complete(remoteOpen.result());
                } else {
                    result.fail(remoteOpen.cause());
                }
            });
            session.open();
        }
        return result;
    }

    Future<ProtonSender> newSender(
            final ProtonConnection connection,
            final ProtonSession session,
            final ResourceIdentifier address,
            final ProtonQoS qos,
            final Handler<ProtonSender> sendQueueDrainHandler,
            final Handler<String> closeHook) {

        final Future<ProtonSender> result = Future.future();
        final ProtonSender sender = session.createSender(getTenantOnlyTargetAddress(address));
        sender.setQoS(qos);
        sender.setAutoSettle(true);
        sender.sendQueueDrainHandler(sendQueueDrainHandler);
        HonoProtonHelper.setCloseHandler(sender, closed ->
            onRemoteDetach(
                    sender,
                    address,
                    connection.getRemoteContainer(),
                    true,
                    closeHook));
        HonoProtonHelper.setDetachHandler(sender, detached -> 
            onRemoteDetach(
                    sender,
                    address,
                    connection.getRemoteContainer(),
                    false,
                    closeHook));
        sender.openHandler(openAttempt -> {
            if (openAttempt.succeeded()) {
                LOG.debug(
                        "sender [{}] for container [{}] open",
                        address,
                        connection.getRemoteContainer());
                result.complete(openAttempt.result());
            } else {
                LOG.debug("could not open sender [{}] for container [{}]",
                        address,
                        connection.getRemoteContainer(), openAttempt.cause());
                result.fail(openAttempt.cause());
            }
        });
        sender.open();
        return result;
    }

    private String getTenantOnlyTargetAddress(final ResourceIdentifier id) {
        final String pathSeparator = config == null ? Constants.DEFAULT_PATH_SEPARATOR : config.getPathSeparator();
        return String.format("%s%s%s", id.getEndpoint(), pathSeparator, id.getTenantId());
    }

    private static void onRemoteDetach(
            final ProtonLink<?> link,
            final ResourceIdentifier targetAddress,
            final String remoteContainer,
            final boolean closed,
            final Handler<String> closeHook) {

        final ErrorCondition error = link.getRemoteCondition();
        final String type = link instanceof ProtonSender ? "sender" : "receiver";
        final String address = link instanceof ProtonSender ? link.getTarget().getAddress() :
            link.getSource().getAddress();
        if (error == null) {
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]",
                    type, address, closed, remoteContainer);
        } else {
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]: {} - {}",
                    type, address, closed, remoteContainer, error.getCondition(), error.getDescription());
        }
        link.close();
        if (HonoProtonHelper.isLinkEstablished(link) && closeHook != null) {
            closeHook.handle(targetAddress.getResourceId());
        }
    }
}
