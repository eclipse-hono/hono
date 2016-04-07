/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class ProtonClientImpl implements ProtonClient {

    private final Vertx vertx;

    public ProtonClientImpl(Vertx vertx) {
        this.vertx = vertx;
    }

    public void connect(String host, int port, Handler<AsyncResult<ProtonConnection>> handler) {
        connect(host, port, null, null, handler);
    }

    public void connect(String host, int port, String username, String password, Handler<AsyncResult<ProtonConnection>> handler) {
        connect(new ProtonClientOptions(), host, port, username, password, handler);
    }

    public void connect(ProtonClientOptions options, String host, int port, Handler<AsyncResult<ProtonConnection>> handler) {
        connect(options, host, port, null, null, handler);
    }

    public void connect(ProtonClientOptions options, String host, int port, String username, String password, Handler<AsyncResult<ProtonConnection>> handler) {
        final NetClient netClient = vertx.createNetClient(options);
        connectNetClient(netClient, host, port, username, password, handler, options);
    }

    private void connectNetClient(NetClient netClient, String host, int port, String username, String password, Handler<AsyncResult<ProtonConnection>> connectHandler, ProtonClientOptions options) {
        netClient.connect(port, host, res -> {
            if (res.succeeded()) {
                ProtonConnectionImpl amqpConnnection = new ProtonConnectionImpl(vertx, host);

                ProtonSaslClientAuthenticatorImpl authenticator = new ProtonSaslClientAuthenticatorImpl(username, password, options.getAllowedSaslMechanisms(), res.result(), connectHandler, amqpConnnection);
                amqpConnnection.bindClient(netClient, res.result(), authenticator);

                //Need to flush here to get the SASL process going, or it will wait until calls on the connection are processed later (e.g open()).
                //TODO: Would that actually be ok? Would remove this complexity and leave Connect being just about the TCP/SSL connection...
                //      but we would then need to handle the peer sending their SASL details before we do, which is allowed.
                amqpConnnection.flush();
            } else {
                connectHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }
}
