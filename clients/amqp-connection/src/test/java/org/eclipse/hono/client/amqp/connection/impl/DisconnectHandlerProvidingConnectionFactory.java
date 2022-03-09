/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.amqp.connection.impl;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A connection factory that provides access to the disconnect handler registered with
 * a connection created by the factory.
 *
 */
public class DisconnectHandlerProvidingConnectionFactory implements ConnectionFactory {

    private final ProtonConnection connectionToCreate;
    private final AtomicInteger connectInvocations = new AtomicInteger(0);

    private Handler<ProtonConnection> disconnectHandler;
    private Handler<AsyncResult<ProtonConnection>> closeHandler;
    private CountDownLatch expectedSucceedingConnectionAttempts;
    private CountDownLatch expectedFailingConnectionAttempts;
    private Throwable causeForFailure;

    DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate) {
        this.connectionToCreate = Objects.requireNonNull(conToCreate);
        failWith(new IllegalStateException("connection refused"));
        setExpectedFailingConnectionAttempts(0);
        setExpectedSucceedingConnectionAttempts(1);
    }

    @Override
    public Future<ProtonConnection> connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler) {
        return connect(options, null, null, closeHandler, disconnectHandler);
    }

    @Override
    public Future<ProtonConnection> connect(
            final ProtonClientOptions options,
            final String username,
            final String password,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler) {
        return connect(options, null, null, null, closeHandler, disconnectHandler);
    }

    @Override
    public Future<ProtonConnection> connect(
            final ProtonClientOptions options,
            final String username,
            final String password,
            final String containerId,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        connectInvocations.incrementAndGet();
        this.closeHandler = closeHandler;
        this.disconnectHandler = disconnectHandler;
        if (expectedFailingConnectionAttempts.getCount() > 0) {
            expectedFailingConnectionAttempts.countDown();
            return Future.failedFuture(causeForFailure);
        } else {
            expectedSucceedingConnectionAttempts.countDown();
            return Future.succeededFuture(connectionToCreate);
        }
    }

    @Override
    public String getHost() {
        return "server";
    }

    @Override
    public int getPort() {
        return Constants.PORT_AMQP;
    }

    @Override
    public String getPathSeparator() {
        return Constants.DEFAULT_PATH_SEPARATOR;
    }

    /**
     * Gets the disconnect handler which will be invoked on loss of
     * connection.
     *
     * @return The handler.
     */
    public Handler<ProtonConnection> getDisconnectHandler() {
        return disconnectHandler;
    }

    /**
     * Gets the handler which will be invoked when the peer closes
     * the connection.
     *
     * @return The handler.
     */
    public Handler<AsyncResult<ProtonConnection>> getCloseHandler() {
        return closeHandler;
    }

    /**
     * Sets the number of connection attempts that this factory should fail
     * before succeeding.
     *
     * @param attempts The number of attempts.
     * @return This factory for command chaining.
     * @see #awaitFailure()
     */
    public DisconnectHandlerProvidingConnectionFactory setExpectedFailingConnectionAttempts(final int attempts) {
        expectedFailingConnectionAttempts = new CountDownLatch(attempts);
        return this;
    }

    /**
     * Sets the number of successful invocations of the connect method that this factory should expect.
     *
     * @param attempts The number of attempts.
     * @return This factory for command chaining.
     * @see #await()
     */
    public DisconnectHandlerProvidingConnectionFactory setExpectedSucceedingConnectionAttempts(final int attempts) {
        expectedSucceedingConnectionAttempts = new CountDownLatch(attempts);
        return this;
    }

    /**
     * Sets the root cause that this factory should fail connection attempts with.
     *
     * @param cause The root cause.
     * @return This factory for command chaining.
     */
    public DisconnectHandlerProvidingConnectionFactory failWith(final Throwable cause) {
        this.causeForFailure = Objects.requireNonNull(cause);
        return this;
    }

    /**
     * Waits for the expected number of succeeding connection attempts to
     * occur.
     *
     * @return {@code true} if the expected number of attempts have succeeded.
     */
    public boolean await() {
        try {
            expectedSucceedingConnectionAttempts.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Waits for the expected number of failing connection attempts to
     * occur.
     *
     * @return {@code true} if the expected number of attempts have failed.
     */
    public boolean awaitFailure() {
        try {
            expectedFailingConnectionAttempts.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gets the number of times the <em>connect</em> method got invoked up to now.
     *
     * @return The number of connect invocations.
     */
    public int getConnectInvocations() {
        return connectInvocations.get();
    }
}
