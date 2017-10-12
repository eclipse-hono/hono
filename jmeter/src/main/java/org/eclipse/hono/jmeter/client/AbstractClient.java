package org.eclipse.hono.jmeter.client;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.proton.ProtonClientOptions;

public class AbstractClient {

    static final int    DEFAULT_CONNECT_TIMEOUT_MILLIS            = 2000;
    static final int    DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS = 2000;
    static final String TIME_STAMP_VARIABLE                       = "timeStamp";

    Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(new AddressResolverOptions()
                        .setCacheNegativeTimeToLive(0) // discard failed DNS lookup results immediately
                        .setCacheMaxTimeToLive(0) // support DNS based service resolution
                        .setRotateServers(true)
                        .setQueryTimeout(DEFAULT_ADDRESS_RESOLUTION_TIMEOUT_MILLIS));
        return Vertx.vertx(options);
    }

    ProtonClientOptions getClientOptions(int reconnectAttempts) {
        return new ProtonClientOptions().setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS)
                .setReconnectAttempts(reconnectAttempts);
    }
}
