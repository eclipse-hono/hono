/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.example;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

import static io.vertx.proton.ProtonHelper.message;
import static io.vertx.proton.ProtonHelper.tag;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HelloWorld {

    public static void main(String[] args) {

        // Create the Vert.x instance
        Vertx vertx = Vertx.vertx();

        // Create the Vert.x AMQP client instance
        ProtonClient client = ProtonClient.create(vertx);

        // Connect, then use the event loop thread to process the connection
        client.connect("localhost", 5672, res -> {
            if (res.succeeded()) {
                System.out.println("We're connected");

                ProtonConnection connection = res.result();
                helloWorldSendAndConsumeExample(connection);
            } else {
                res.cause().printStackTrace();
            }
        });

        // Just stop main() from exiting
        try {
            System.in.read();
        } catch (Exception ignore) {
        }
    }

    private static void helloWorldSendAndConsumeExample(ProtonConnection connection) {
        connection.open();

        // Receive messages from queue "foo" (using an ActiveMQ style address as example).
        String address = "queue://foo";

        connection.createReceiver(address)
            .handler((delivery, msg) -> {
                Section body = msg.getBody();
                if (body instanceof AmqpValue) {
                    String content = (String) ((AmqpValue) body).getValue();
                    System.out.println("Received message with content: " + content);
                }
                // By default, the receiver automatically accepts (and settles) the delivery
                // when the handler returns, if no other disposition has been applied.
                // To change this and always manage dispositions yourself, use the
                // setAutoAccept method on the receiver.
            })
            .flow(10)  // Prefetch up to 10 messages. The client will replenish credit as deliveries are settled.
            .open();

        // Create an anonymous sender, have the message carry the destination
        ProtonSender sender = connection.createSender(null);

        // Send message to the queue..
        Message message = message(address, "Hello World from client");

        // Can optionally add an openHandler or sendQueueDrainHandler
        // to await remote sender open completing or credit to send being
        // granted. But here we will just buffer the send immediately.
        sender.open();
        System.out.println("Sending message to server");
        sender.send(tag("m1"), message, delivery -> {
            System.out.println("The message was received by the server");
        });
    }

}
