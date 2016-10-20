package org.eclipse.hono.consumer.dweet;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.main.Main;

/**
 * A Camel Route for publishing telemetry data from Hono to dweet.io.
 */
public class DweetRoute extends RouteBuilder {

    /**
     * A main() so we can easily run these routing rules in our IDE/from command line
     */
    public static void main(String... args) throws Exception {
        System.out.println("set the thingName system property to your device's name on dweet.io ...");
        Main main = new Main();
        main.addRouteBuilder(new DweetRoute());
        main.run(args);
    }

    public void configure() {

        String thingName = System.getProperty("thingName", "hono-demo");
        String honoHost = System.getProperty("hono.client.host", "localhost");
        String honoPort = System.getProperty("hono.client.port", "15672");
        String honoUsername = System.getProperty("hono.client.username", "user1@HONO");
        String honoPassword = System.getProperty("hono.client.password", "pw");
        
        getContext().addComponent("amqp", AMQPComponent.amqpComponent(String.format("amqp://%s:%s", honoHost, honoPort), honoUsername, honoPassword));
        from("amqp:telemetry/DEFAULT_TENANT")
            .choice()
                .when(header("object").isNotNull())
                    .log(LoggingLevel.INFO, String.format("forwarding telemetry data [object=${in.header.object}] to dweet.io [thing=%s]: ${in.body}", thingName))
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .setHeader(Exchange.CONTENT_TYPE, simple("${in.header.content_type}"))
                    .to(String.format("http4://dweet.io/dweet/for/%s", thingName))
                .otherwise()
                    .log(LoggingLevel.INFO, "${in.body}");
    }
}
