/*
TODO
 */

package org.eclipse.hono.deviceconnection.redis.client;

import io.smallrye.config.RelocateConfigSourceInterceptor;

/**
 * TODO.
 */
public class RelocateRedisConfigInterceptor extends RelocateConfigSourceInterceptor {
    /**
     * TODO.
     */
    public RelocateRedisConfigInterceptor() {
        super(name -> name.startsWith("quarkus.redis") ?
                name.replaceAll("quarkus\\.redis", "hono.commandRouter.cache.redis") : name);
    }
}
