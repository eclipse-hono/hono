package org.eclipse.hono.example.protocoladapter.adapter;

import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.adapter.AmqpCliClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adapter for telemetry or event messages using methods and properties from {@link AmqpCliClient} to simplify handling
 * 
 * based loosely on {@link org.eclipse.hono.cli.adapter.TelemetryAndEventCli}
 * 
 * @see org.eclipse.hono.cli.adapter.TelemetryAndEventCli
 */
@Component
public class TelemetryAndEventSender extends AmqpCliClient {
    
    /**
     * Sends message to Hono AMQP adapter
     * 
     * @param messagePayload message payload
     * @param messageAddress "telemetry" ("t") or "event" ("e")
     * @param messageTracker message delivery Future
     */
    public void sendMessage(final String messagePayload, final String messageAddress, final CompletableFuture<ProtonDelivery> messageTracker) throws IllegalArgumentException{
        String messageAddressChecked;
        switch (messageAddress.toLowerCase()) {
            case "telemetry":
            case "t":
                messageAddressChecked = "telemetry";
                break;
            case "event":
            case "e":
                messageAddressChecked = "event";
                break;
            default:
                throw new IllegalArgumentException(String.format("Illegal argument for messageAddress: \"%s\"", messageAddress));
        }

        connectToAdapter()
                .compose(con -> {
                    adapterConnection = con;
                    return createSender();
                })
                .map(sender -> {
                    final Message message = ProtonHelper.message(messageAddressChecked, messagePayload);
                    sender.send(message, delivery -> {
                        adapterConnection.close();
                        messageTracker.complete(delivery);
                    });
                    return sender;
                })
                .otherwise(t -> {
                    messageTracker.completeExceptionally(t);
                    return null;
                });


    }

    /**
     *  Sets AMQP client connection properties
     * 
     * @param host AMQP Hono adapter IP address
     * @param port AMQP Hono adapter port
     * @param username username consists of DEVICE_ID@TENANT_ID
     * @param password device credentials
     */
    public void setAMQPClientProps(final String host, final int port, final String username, final String password) {
        ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(host);
        props.setPort(port);
        props.setUsername(username);
        props.setPassword(password);

        setClientConfig(props);
    }
}

