package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.AbstractHonoConsumer;

/**
 * Example class with minimal dependencies for consuming telemetry data from Hono.
 *
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure where Hono's
 * microservices are reachable.
 */
public class HonoTelemetryConsumer extends AbstractHonoConsumer {
    public static void main(final String[] args) throws Exception {
        System.out.println("Starting telemetry consumer...");
        HonoTelemetryConsumer honoDownstreamEventConsumer = new HonoTelemetryConsumer();
        honoDownstreamEventConsumer.consumeData();
        System.out.println("Finishing telemetry consumer.");
    }

}
