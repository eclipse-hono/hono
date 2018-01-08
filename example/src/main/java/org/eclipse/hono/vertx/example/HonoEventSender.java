package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.HonoSenderBase;

/**
 * Example class with minimal dependencies for sending event data to Hono.
 * <p>
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure where Hono's
 * microservices are reachable.
 */
public class HonoEventSender extends HonoSenderBase {

    public static void main(final String[] args) {

        System.out.println("Starting downstream event sender...");
        HonoEventSender honoDownstreamSender = new HonoEventSender();
        honoDownstreamSender.setEventMode(true);
        honoDownstreamSender.sendData();
        System.out.println("Finishing downstream event sender.");
    }
}
