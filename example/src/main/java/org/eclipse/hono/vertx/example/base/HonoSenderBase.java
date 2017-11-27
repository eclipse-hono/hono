package org.eclipse.hono.vertx.example.base;

import io.vertx.core.*;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonDelivery;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.vertx.example.base.HonoExampleConstants.*;

/**
 * Example base class for sending data to Hono.
 * <p>
 * This class implements all necessary code to get Hono's client running. It sends data 50 times
 * in sequence and shows the necessary programming patterns for that.
 * <p>
 * By default, this class sends telemetry data. This can be changed to event data by setting
 * {@link HonoSenderBase#setEventMode(boolean)} to true.
 */
public class HonoSenderBase {
    public static final int COUNT = 50;
    public static final String HONO_CLIENT_USER = "hono-client@HONO";
    public static final String HONO_CLIENT_PASSWORD = "secret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoRegistryClient;
    private final HonoClient honoMessagingClient;

    private RegistrationClient registrationClient;
    private MessageSender messageSender;

    private boolean eventMode = false;

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
                        .host(HONO_MESSAGING_HOST)
                        .port(HONO_MESSAGING_PORT)
                        .user(HONO_CLIENT_USER)
                        .password(HONO_CLIENT_PASSWORD)
                        .trustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        honoRegistryClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HONO_REGISTRY_HOST)
                        .port(HONO_REGISTRY_PORT)
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
        }

        // finally close down vertx.
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
        Future<Void> result = Future.future();
        final Map<String, Object> properties = new HashMap<>();
        properties.put("my_prop_string", "I'm a string");
        properties.put("my_prop_int", value);

        boolean messageWasSent;
        if (eventMode) {
            // define a disposition handler to get information about the delivery
            final BiConsumer<Object, ProtonDelivery> myDispositionHandler = (messageId, delivery) -> {
                if (! (delivery.getRemoteState() instanceof Accepted)) {
                    System.err.println("delivery failed for [message ID: " + messageId + ", new remote state: " + delivery.getRemoteState());
                } else {
                    System.out.println("delivery accepted for [message ID: " + messageId);
                }
            };
            messageWasSent = messageSender.send(DEVICE_ID, properties, "myMessage" + value, "text/plain", token, myDispositionHandler);
        } else {
            messageWasSent = messageSender.send(DEVICE_ID, properties, "myMessage" + value, "text/plain", token);
        }
        if (messageWasSent) {
            result.complete();
        } else {
            result.fail("Sender might have no credits, is a consumer attached?");
        }
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
            honoRegistryClient.getOrCreateRegistrationClient(TENANT_ID, registrationClientTracker.completer());
        }, registrationClientTracker);

        return registrationClientTracker;
    }

    private Future<MessageSender> getMessageSender() {
        final Future<MessageSender> messageSenderTracker = Future.future();

        final Future<HonoClient> messagingConnectionTracker = Future.future();
        honoMessagingClient.connect(new ProtonClientOptions(), messagingConnectionTracker.completer());
        messagingConnectionTracker.compose(messagingClient -> {
            if (eventMode) {
                messagingClient.getOrCreateEventSender(TENANT_ID, messageSenderTracker.completer());
            } else {
                messagingClient.getOrCreateTelemetrySender(TENANT_ID, messageSenderTracker.completer());
            }
        }, messageSenderTracker);

        return messageSenderTracker;
    }

    private void handleSingleMessage(final int value) {
        final CountDownLatch messageSenderLatch = new CountDownLatch(1);

        final Future<Void> senderTracker = Future.future();
        senderTracker.setHandler(v -> {
            if (senderTracker.failed()) {
                System.err.println("Failed: Sending message... #" + value + " - " + senderTracker.toString());
            } else {
                System.out.println("Sending message... #" + value);
            }
            messageSenderLatch.countDown();
        });
        getRegistrationAssertion().compose(token ->
                sendMessageToHono(value, token)
        ).compose(v -> senderTracker.complete(),
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
        registrationClient.assertRegistration(DEVICE_ID, tokenTracker.completer());

        tokenTracker.compose(regResult -> {
            if (regResult.getStatus() == HTTP_OK) {
                result.complete(regResult.getPayload().getString(RegistrationConstants.FIELD_ASSERTION));
            } else if (regResult.getStatus() == HTTP_NOT_FOUND) {
                result.fail("cannot assert registration status (device needs to be registered)");
            } else {
                result.fail("cannot assert registration status : " + regResult.getStatus());
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
    public void setEventMode(boolean value) {
        this.eventMode = value;
    }
}
