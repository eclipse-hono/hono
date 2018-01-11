package org.eclipse.hono.vertx.example.base;

import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

import static org.eclipse.hono.vertx.example.base.HonoExampleConstants.DEVICE_ID;

/**
 * Example base class for sending data to Hono.
 * <p>
 * This class implements all necessary code to get Hono's client running. It sends data 50 times
 * in sequence and shows the necessary programming patterns for that. At the end, it prints a summary of the delivery
 * of messages.
 * <p>
 * By default, this class sends telemetry data. This can be changed to event data by setting
 * {@link HonoSenderBase#setEventMode(boolean)} to true.
 */
public class HonoSenderBase {

    /**
     * The number of messages to send.
     */
    public static final int COUNT = 2500;
    /**
     * The user name for connecting to Hono.
     */
    public static final String HONO_CLIENT_USER = "hono-client@HONO";
    /**
     * The password for connecting to Hono.
     */
    public static final String HONO_CLIENT_PASSWORD = "secret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoRegistryClient;
    private final HonoClient honoMessagingClient;

    private RegistrationClient registrationClient;
    private MessageSender messageSender;

    private boolean eventMode = false;

    /**
     * A countdown latch to track if all messages were sent correctly.
     */
    private CountDownLatch messageDeliveryCountDown = new CountDownLatch(COUNT);
    /**
     * Count failed message deliveries.
     */
    private AtomicInteger nrMessageDeliveryFailed = new AtomicInteger(0);
    /**
     * Count successful message deliveries.
     */
    private AtomicInteger nrMessageDeliverySucceeded = new AtomicInteger(0);

    /**
     * The sender needs two connections to Hono:
     * <ul>
     * <li>one to HonoMessaging (to send data downstream),
     * <li>and one to Hono's Device Registry (to obtain a registration assertion that needs to be sent along with the data).
     * </ul>
     * <p>
     * These clients are instantiated here.
     * <p>
     * NB: if you want to integrate this code with your own software, it might be necessary to copy the truststore to
     * your project as well and adopt the file path.
     */
    public HonoSenderBase() {
        honoMessagingClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HonoExampleConstants.HONO_MESSAGING_HOST)
                        .port(HonoExampleConstants.HONO_MESSAGING_PORT)
                        .user(HONO_CLIENT_USER)
                        .password(HONO_CLIENT_PASSWORD)
                        .trustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        honoRegistryClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HonoExampleConstants.HONO_REGISTRY_HOST)
                        .port(HonoExampleConstants.HONO_REGISTRY_PORT)
                        .user(HONO_CLIENT_USER)
                        .password(HONO_CLIENT_PASSWORD)
                        .trustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
    }

    /**
     * Send data to HonoMessaging {@link HonoSenderBase#COUNT} times in a sequence.
     * First all Hono clients need to be connected before data can be sent.
     */
    protected void sendData() {
        final CompletableFuture<String> tokenDone = new CompletableFuture<>();

        getHonoClients().compose(ok -> getRegistrationAssertion()).map(receivedToken -> {
            tokenDone.complete(receivedToken);
            return null;
        }).
        otherwise(t -> {
            System.err.println("cannot send messages: " + t.getMessage());
            tokenDone.completeExceptionally(t);
            return null;
        });

        try {
            // wait for token from registration service
            final String token = tokenDone.get();
            // then send single messages sequentially in a loop
            for (int messagesSent = 0; messagesSent < COUNT; messagesSent++) {
                // send message and wait until it was delivered
                sendMessageToHono(messagesSent, token).get();
                if (messagesSent % 250 == 0) {
                    System.out.println("Sent " + messagesSent + " messages.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        closeClients();
        // print a summary of the message deliveries.
        System.out.println("Total number of messages: " + COUNT);
        System.out.println("Successful deliveries   : " + nrMessageDeliverySucceeded + (eventMode ? " (incl. acknowledge)." : "."));
        System.out.println("Failed deliveries       : " + nrMessageDeliveryFailed.get());

        // give some time for flushing asynchronous message buffers before shutdown
        vertx.setTimer(2000, timerId -> {
            vertx.close();
        });
    }

    /**
     * Use the message sender of Hono that was provided by the Hono client to send data downstream.
     *
     * @param value The int value that is combined with a string and send downstream.
     * @param token The registration assertion that was retrieved for the device to let HonoMessaging verify the
     *              authorization to send data.
     * @return A Future that is completed after the message was sent.
     */
    private CompletableFuture<Void> sendMessageToHono(final int value, final String token) {

        final CompletableFuture<Void> capacityAvailableFuture = new CompletableFuture<>();
        final CompletableFuture<Void> messageDeliveredFuture = new CompletableFuture<>();
        final CompletableFuture<Void> result = messageDeliveredFuture.thenCombine(capacityAvailableFuture,
                (v1, v2) -> (Void) null
        );

        messageSender.send(HonoExampleConstants.DEVICE_ID, null, "myMessage" + value, "text/plain",
            token, capacityAvail -> {
                capacityAvailableFuture.complete(null);
            }).map(delivery -> {
                nrMessageDeliverySucceeded.incrementAndGet();
                messageDeliveredFuture.complete(null);
                return (Void) null;
            }).otherwise(t -> {
                System.err.println("Could not send message: " + t.getMessage());
                nrMessageDeliveryFailed.incrementAndGet();
                result.completeExceptionally(t);
                return (Void) null;
            });
        messageDeliveryCountDown.countDown();
        return result;
    }

    /**
     * Get both Hono clients and connect them to Hono's microservices.
     *
     * @return The result of the creation and connection of the Hono clients.
     */
    private Future<Void> getHonoClients() {
        // we need two clients to get it working, define futures for them
        final Future<RegistrationClient> registrationClientTracker = getRegistrationClient();
        final Future<MessageSender> messageSenderTracker = getMessageSender();

        final Future<Void> result = Future.future();

        CompositeFuture.all(registrationClientTracker, messageSenderTracker).setHandler(s -> {
            if (result.failed()) {
                System.err.println(
                        "hono clients could not be created : " + s.cause().getMessage());
                result.fail(s.cause());
            } else {
                registrationClient = registrationClientTracker.result();
                messageSender = messageSenderTracker.result();
                result.complete();
            }
        });

        return result;
    }

    private Future<RegistrationClient> getRegistrationClient() {

        final Future<RegistrationClient> result = Future.future();

        final Future<HonoClient> registryConnectionTracker = Future.future();
        honoRegistryClient.connect(new ProtonClientOptions(), registryConnectionTracker.completer());
        registryConnectionTracker.compose(registryClient -> {
            honoRegistryClient.getOrCreateRegistrationClient(HonoExampleConstants.TENANT_ID, result.completer());
        }, result);

        return result;
    }

    private Future<MessageSender> getMessageSender() {

        final Future<MessageSender> result = Future.future();

        final Future<HonoClient> messagingConnectionTracker = Future.future();
        honoMessagingClient.connect(new ProtonClientOptions(), messagingConnectionTracker.completer());
        messagingConnectionTracker.compose(messagingClient -> {
            if (isEventMode()) {
                messagingClient.getOrCreateEventSender(HonoExampleConstants.TENANT_ID, result.completer());
            } else {
                messagingClient.getOrCreateTelemetrySender(HonoExampleConstants.TENANT_ID, result.completer());
            }
        }, result);

        return result;
    }

    /**
     * Get the registration assertion for the device that needs to be sent along with the data downstream to HonoMessaging.
     * 
     * @return The assertion inside the Future if it was successful, the error reason inside the Future if it failed.
     */
    private Future<String> getRegistrationAssertion() {

        final Future<RegistrationResult> tokenTracker = Future.future();
        registrationClient.assertRegistration(DEVICE_ID, tokenTracker.completer());

        return tokenTracker.map(regResult -> {
            if (regResult.getStatus() == HttpURLConnection.HTTP_OK) {
                 return regResult.getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
            } else {
                throw new IllegalStateException("cannot assert registration status");
            }
        }).otherwise(t -> {
            throw new IllegalStateException("cannot assert registration status", t);
        });
    }

    private Future<Void> closeClients() {
        final Future<Void> messagingClient = Future.future();
        final Future<Void> regClient = Future.future();
        honoMessagingClient.shutdown(messagingClient.completer());
        honoRegistryClient.shutdown(regClient.completer());
        return CompositeFuture.all(messagingClient, regClient).compose(ok -> Future.succeededFuture());
    }

    /**
     * Gets if event data or telemetry data is sent.
     *
     * @return True if only event data is sent, false if only telemetry data is sent.
     */
    public boolean isEventMode() {
        return eventMode;
    }

    /**
     * Sets the sender to send event data or telemetry data.
     *
     * @param value The new value for the event mode.
     */
    public void setEventMode(final boolean value) {
        this.eventMode = value;
    }
}
