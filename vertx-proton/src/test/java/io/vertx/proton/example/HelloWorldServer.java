/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.example;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonServer;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

import static io.vertx.proton.ProtonHelper.message;
import static io.vertx.proton.ProtonHelper.tag;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class HelloWorldServer {

    public static void main(String[] args) {

        // Create the Vert.x instance
        Vertx vertx = Vertx.vertx();

        // Create the Vert.x AMQP client instance
        ProtonServer server = ProtonServer.create(vertx)
                .connectHandler((connection) -> {
                    helloProcessConnection(vertx, connection);
                })
                .listen(5672, (res) -> {
                    if (res.succeeded()) {
                        System.out.println("Listening on: " + res.result().actualPort());
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

    private static void helloProcessConnection(Vertx vertx, ProtonConnection connection) {
        connection.openHandler(res ->{
            System.out.println("Client connected: "+connection.getRemoteContainer());
        }).closeHandler(c -> {
            System.out.println("Client closing amqp connection: " + connection.getRemoteContainer());
            connection.close();
            connection.disconnect();
        }).disconnectHandler(c->{
            System.out.println("Client socket disconnected: "+connection.getRemoteContainer());
            connection.disconnect();
        }).open();
        connection.sessionOpenHandler(session -> session.open());

        connection.receiverOpenHandler(receiver -> {
            receiver
                .setTarget(receiver.getRemoteTarget())
                .handler((delivery, msg) -> {

                    String address = msg.getAddress();
                    if( address == null ) {
                        address = receiver.getRemoteTarget().getAddress();
                    }

                    Section body = msg.getBody();
                    if (body instanceof AmqpValue) {
                        String content = (String) ((AmqpValue) body).getValue();
                        System.out.println("message to:"+address+", body: " + content);
                    }
                })
                .flow(10)
                .open();
        });

        connection.senderOpenHandler(sender -> {
            System.out.println("Sending to client from: " + sender.getRemoteSource().getAddress());
            sender.setSource(sender.getRemoteSource()).open();
            vertx.setPeriodic(1000, timer -> {
                if (connection.isDisconnected()) {
                    vertx.cancelTimer(timer);
                } else {
                    System.out.println("Sending message to client");
                    Message m = message("Hello World from Server!");
                    sender.send(tag("m1"), m, delivery -> {
                        System.out.println("The message was received by the client.");
                    });
                }
            });
        });


    }

}
