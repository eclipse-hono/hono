package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.AbstractHonoSender;

/**
 * Example class with minimal dependencies for sending telemetry data to Hono.
 *
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure where Hono's
 * microservices are reachable.
 */
public class HonoTelemetrySender extends AbstractHonoSender {
    public static void main(final String[] args) throws Exception {
        System.out.println("Starting downstream telemetry sender...");
        HonoTelemetrySender honoDownstreamSender = new HonoTelemetrySender();
        honoDownstreamSender.sendData();
        System.out.println("Finishing downstream telemetry sender.");
    }
}
