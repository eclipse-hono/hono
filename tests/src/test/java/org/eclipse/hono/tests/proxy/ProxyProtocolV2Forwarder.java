/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.proxy;

import java.net.InetAddress;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

/**
 * A small TCP forwarder that prepends a Proxy Protocol v2 header.
 */
public final class ProxyProtocolV2Forwarder {

    private static final byte[] PP2_SIGNATURE = new byte[] {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    private final Vertx vertx;
    private final NetServer server;
    private final NetClient client;
    private final String targetHost;
    private final int targetPort;
    private final int proxyDestinationPort;
    private final InetAddress sourceAddress;

    private ProxyProtocolV2Forwarder(
            final Vertx vertx,
            final String targetHost,
            final int targetPort,
            final int proxyDestinationPort,
            final InetAddress sourceAddress) {
        this.vertx = vertx;
        this.server = vertx.createNetServer(new NetServerOptions().setHost("127.0.0.1").setPort(0));
        this.client = vertx.createNetClient(new NetClientOptions());
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.proxyDestinationPort = proxyDestinationPort;
        this.sourceAddress = sourceAddress;
    }

    /**
     * Starts a Proxy Protocol v2 forwarder.
     *
     * @param vertx The vert.x instance.
     * @param targetHost The backend host.
     * @param targetPort The backend port.
     * @param sourceIp The source IP to encode in Proxy Protocol headers.
     * @return A future succeeding with the started forwarder.
     */
    public static Future<ProxyProtocolV2Forwarder> start(
            final Vertx vertx,
            final String targetHost,
            final int targetPort,
            final String sourceIp) {
        return start(vertx, targetHost, targetPort, sourceIp, targetPort);
    }

    /**
     * Starts a Proxy Protocol v2 forwarder.
     *
     * @param vertx The vert.x instance.
     * @param targetHost The backend host.
     * @param targetPort The backend port.
     * @param sourceIp The source IP to encode in Proxy Protocol headers.
     * @param proxyDestinationPort The destination port to encode in Proxy Protocol headers.
     * @return A future succeeding with the started forwarder.
     */
    public static Future<ProxyProtocolV2Forwarder> start(
            final Vertx vertx,
            final String targetHost,
            final int targetPort,
            final String sourceIp,
            final int proxyDestinationPort) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(targetHost);
        Objects.requireNonNull(sourceIp);

        final Promise<ProxyProtocolV2Forwarder> result = Promise.promise();

        try {
            final var forwarder = new ProxyProtocolV2Forwarder(
                    vertx,
                    targetHost,
                    targetPort,
                    proxyDestinationPort,
                    InetAddress.getByName(sourceIp));
            forwarder.startInternal().onComplete(result);
        } catch (final Exception e) {
            result.fail(e);
        }

        return result.future();
    }

    private Future<ProxyProtocolV2Forwarder> startInternal() {

        final Promise<ProxyProtocolV2Forwarder> result = Promise.promise();

        server.connectHandler(inbound -> {
            inbound.pause();
            client.connect(targetPort, targetHost).onComplete(connectAttempt -> {
                if (connectAttempt.failed()) {
                    inbound.close();
                    return;
                }

                final NetSocket outbound = connectAttempt.result();
                final Buffer header = createHeader(inbound.remoteAddress());
                outbound.write(header).onFailure(cause -> {
                    inbound.close();
                    outbound.close();
                }).onSuccess(ok -> {
                    inbound.resume();

                    inbound.pipeTo(outbound).onFailure(cause -> {
                        inbound.close();
                        outbound.close();
                    });
                    outbound.pipeTo(inbound).onFailure(cause -> {
                        inbound.close();
                        outbound.close();
                    });
                });
            });
        });

        server.listen().onComplete(listenAttempt -> {
            if (listenAttempt.succeeded()) {
                result.complete(this);
            } else {
                result.fail(listenAttempt.cause());
            }
        });

        return result.future();
    }

    /**
     * Gets the local port this forwarder is listening on.
     *
     * @return The local listening port.
     */
    public int localPort() {
        return server.actualPort();
    }

    /**
     * Closes the forwarder.
     *
     * @return A future indicating the close outcome.
     */
    public Future<Void> close() {
        final Promise<Void> result = Promise.promise();
        server.close().onComplete(closeAttempt -> {
            client.close();
            if (closeAttempt.succeeded()) {
                result.complete();
            } else {
                result.fail(closeAttempt.cause());
            }
        });
        return result.future();
    }

    private Buffer createHeader(final SocketAddress remoteAddress) {

        final byte[] source = sourceAddress.getAddress();
        final byte[] destination;
        try {
            destination = InetAddress.getByName(targetHost).getAddress();
        } catch (final Exception e) {
            throw new IllegalStateException("cannot resolve target host for proxy protocol header", e);
        }

        if (source.length != 4 || destination.length != 4) {
            throw new IllegalStateException("proxy protocol v2 forwarder currently supports IPv4 only");
        }

        final int sourcePort = remoteAddress.port();

        return Buffer.buffer()
                .appendBytes(PP2_SIGNATURE)
                .appendByte((byte) 0x21)
                .appendByte((byte) 0x11)
                .appendUnsignedShort(12)
                .appendBytes(source)
                .appendBytes(destination)
                .appendUnsignedShort(sourcePort)
                .appendUnsignedShort(proxyDestinationPort);
    }

}
