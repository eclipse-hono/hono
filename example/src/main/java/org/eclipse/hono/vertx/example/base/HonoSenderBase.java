package org.eclipse.hono.vertx.example.base;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

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
    public static final int COUNT = 50;
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
     *
     * @throws InterruptedException If the used latch was interrupted during waiting.
     */
    protected void sendData() throws InterruptedException {
        // use a latch to wait for the Hono clients to be connected.
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicBoolean clientsCreated = getHonoClients(latch);

        latch.await();

        // check if the clients were created properly
        if (clientsCreated.get()) {
            // then send single messages sequentially in a loop
            IntStream.range(0, COUNT).forEach(value -> {
                handleSingleMessage(value);
            });

            /*
             * Since events are asynchronously acknowledged to validate their delivery, the vertx instance must not be
             * closed before all acknowledgements were processed. Wait for the count down latch here.
             */
            messageDeliveryCountDown.await();
            // print a summary of the message deliveries.
            System.out.println("All " + COUNT + " messages tried to deliver.");
            System.out.println("Successful deliveries: " + nrMessageDeliverySucceeded + (eventMode ? " (incl. acknowledge)." : "."));
            System.out.println("Failed deliveries    : " + nrMessageDeliveryFailed.get());
        }

        vertx.close();
    }

    /**
     * Use the message sender of Hono that was provided by the Hono client to send data downstream.
     *
     * @param value The int value that is combined with a string and send downstream.
     * @param token The registration assertion that was retrieved for the device to let HonoMessaging verify the
     *              authorization to send data.
     * @return A Future that is completed after the message was sent.
     */
    private Future<Void> sendMessageToHono(final int value, final String token) {

        final Map<String, Object> properties = new HashMap<>();
        properties.put("my_prop_string", "I'm a string");
        properties.put("my_prop_int", value);

        Future<Void> result = messageSender.send(HonoExampleConstants.DEVICE_ID, properties, "myMessage" + value, "text/plain", token)
                .recover(t -> {
                    System.err.println("Could not send message: " + t.getMessage());
                    nrMessageDeliveryFailed.incrementAndGet();
                    return Future.failedFuture(t);
                }).compose(delivery -> {
                    nrMessageDeliverySucceeded.incrementAndGet();
                    return Future.<Void> succeededFuture();
                });
        messageDeliveryCountDown.countDown();
        return result;
    }

    /**
     * Get both Hono clients and connect them to Hono's microservices.
     *
     * @param countDownLatch The latch to count down as soon as the clients are created.
     *
     * @return The result of the creation and connection of the Hono clients.
     */
    private AtomicBoolean getHonoClients(final CountDownLatch countDownLatch) {
        // we need two clients to get it working, define futures for them
        final Future<RegistrationClient> registrationClientTracker = getRegistrationClient();
        final Future<MessageSender> messageSenderTracker = getMessageSender();

        final AtomicBoolean clientsCreated = new AtomicBoolean(false);

        CompositeFuture.all(registrationClientTracker, messageSenderTracker).setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println(
                        "hono clients could not be created : " + result.cause().getMessage());
            } else {
                clientsCreated.set(true);
                registrationClient = registrationClientTracker.result();
                messageSender = messageSenderTracker.result();
            }
            countDownLatch.countDown();
        });

        return clientsCreated;
    }

    private Future<RegistrationClient> getRegistrationClient() {
        final Future<RegistrationClient> registrationClientTracker = Future.future();

        final Future<HonoClient> registryConnectionTracker = Future.future();
        honoRegistryClient.connect(new ProtonClientOptions(), registryConnectionTracker.completer());
        registryConnectionTracker.compose(registryClient -> {
            honoRegistryClient.getOrCreateRegistrationClient(HonoExampleConstants.TENANT_ID, registrationClientTracker.completer());
        }, registrationClientTracker);

        return registrationClientTracker;
    }

    private Future<MessageSender> getMessageSender() {
        final Future<MessageSender> messageSenderTracker = Future.future();

        final Future<HonoClient> messagingConnectionTracker = Future.future();
        honoMessagingClient.connect(new ProtonClientOptions(), messagingConnectionTracker.completer());
        messagingConnectionTracker.compose(messagingClient -> {
            if (eventMode) {
                messagingClient.getOrCreateEventSender(HonoExampleConstants.TENANT_ID, messageSenderTracker.completer());
            } else {
                messagingClient.getOrCreateTelemetrySender(HonoExampleConstants.TENANT_ID, messageSenderTracker.completer());
            }
        }, messageSenderTracker);

        return messageSenderTracker;
    }

    private void handleSingleMessage(final int value) {
        final CountDownLatch messageSenderLatch = new CountDownLatch(1);

        final Future<Void> senderTracker = Future.future();
        senderTracker.setHandler(v -> {
            messageSenderLatch.countDown();
            if (senderTracker.failed()) {
                System.err.println("Failed: Sending message... #" + value + " - " + senderTracker.toString());
            } else {
                System.out.println("Sending message... #" + value);
            }
        });
        getRegistrationAssertion().compose(token ->
                sendMessageToHono(value, token)
        ).compose(v -> {
                    nrMessageDeliverySucceeded.incrementAndGet();
                    senderTracker.complete();
                },
                senderTracker);

        try {
            messageSenderLatch.await();
        } catch (InterruptedException e) {
        }
    }

    /**
     * Get the registration assertion for the device that needs to be sent along with the data downstream to HonoMessaging.
     * @return The assertion inside the Future if it was successful, the error reason inside the Future if it failed.
     */
    private Future<String> getRegistrationAssertion() {

        final Future<String> result = Future.future();
        final Future<RegistrationResult> tokenTracker = Future.future();
        registrationClient.assertRegistration(HonoExampleConstants.DEVICE_ID, tokenTracker.completer());

        tokenTracker.compose(regResult -> {
            if (regResult.getStatus() == HttpURLConnection.HTTP_OK) {
                result.complete(regResult.getPayload().getString(RegistrationConstants.FIELD_ASSERTION));
            } else if (regResult.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                result.fail(new ClientErrorException(regResult.getStatus(), "cannot assert registration status (device needs to be registered)"));
            } else {
                result.fail(new ServiceInvocationException(regResult.getStatus(), "cannot assert registration status"));
            }
        }, result);
        return result;
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
