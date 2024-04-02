/*
TODO
 */
package org.eclipse.hono.commandrouter.redis.config;

import io.smallrye.config.RelocateConfigSourceInterceptor;

/**
 * TODO.
 */
public class RedisConfigInterceptor extends RelocateConfigSourceInterceptor {

    /**
     * TODO.
     */
    public RedisConfigInterceptor() {
        super(name -> {

            return name.startsWith("quarkus.redis") ?
                    name.replaceAll("quarkus\\.redis", "hono.commandRouter.cache.redis") : name;
        });
    }
}
