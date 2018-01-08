package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.HonoConsumerBase;

/**
 * Example class with minimal dependencies for consuming event data from Hono.
 * <p>
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure where Hono's
 * microservices are reachable.
 */
public class HonoEventConsumer extends HonoConsumerBase {

    public static void main(final String[] args) throws Exception {

        System.out.println("Starting downstream consumer...");
        HonoEventConsumer honoDownstreamEventConsumer = new HonoEventConsumer();
        honoDownstreamEventConsumer.setEventMode(true);
        honoDownstreamEventConsumer.consumeData();
        System.out.println("Finishing downstream consumer.");
    }
}
