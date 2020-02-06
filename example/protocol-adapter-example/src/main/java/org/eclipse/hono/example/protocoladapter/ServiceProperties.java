package org.eclipse.hono.example.protocoladapter;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure vertx, which is used in {@link org.eclipse.hono.cli.adapter.AmqpCliClient}
 */
@Configuration
public class ServiceProperties {
    /**
     * Exposes a Vert.x instance as a Spring bean.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        final VertxOptions options = new VertxOptions().setWarningExceptionTime(1500000000);
        return Vertx.vertx(options);
    }

}