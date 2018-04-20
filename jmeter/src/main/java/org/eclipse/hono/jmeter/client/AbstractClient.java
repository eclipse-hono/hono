package org.eclipse.hono.jmeter.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.proton.ProtonClientOptions;

/**
 * Base class for implementing JMeter samplers connecting to Hono.
 *
 */
public abstract class AbstractClient {

    static final int    DEFAULT_CONNECT_TIMEOUT_MILLIS            = 1000;
    static final int    DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS = 2000;
    static final String TIME_STAMP_VARIABLE                       = "timeStamp";

    /**
     * The vert.x instance to use for connecting to Hono.
     */
    protected final Vertx vertx;

    /**
     * Creates a new client.
     */
    protected AbstractClient() {
        vertx = vertx();
    }

    final Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(new AddressResolverOptions()
                        .setCacheNegativeTimeToLive(0) // discard failed DNS lookup results immediately
                        .setCacheMaxTimeToLive(0) // support DNS based service resolution
                        .setRotateServers(true)
                        .setQueryTimeout(DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS));
        return Vertx.vertx(options);
    }

    final ProtonClientOptions getClientOptions(final int reconnectAttempts) {
        return new ProtonClientOptions().setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setReconnectAttempts(reconnectAttempts);
    }

    /**
     * Closes the vert.x instance.
     * 
     * @return A future that will succeed once the instance is closed.
     */
    protected final Future<Void> closeVertx() {
        final Future<Void> result = Future.future();
        vertx.close(result.completer());
        return result;
    }
}
