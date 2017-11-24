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
 * Abstract example base class for sending data to Hono.
 */
public abstract class AbstractHonoSender {
    public static final int COUNT = 50;
    public static final String HONO_CLIENT_USER = "hono-client@HONO";
    public static final String HONO_CLIENT_PASSWORD = "secret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoRegistryClient;
    private final HonoClient honoMessagingClient;

    private RegistrationClient registrationClient;
    private MessageSender messageSender;

    private boolean eventMode = false;

    public AbstractHonoSender() {
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

    protected void sendData() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicBoolean clientsCreated = getHonoClients(latch);

        latch.await();

        if (clientsCreated.get()) {
            IntStream.range(0, COUNT).forEach(value -> {
                handleSingleMessage(value);
            });
        }

        vertx.close();
    }

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

    public boolean isEventMode() {
        return eventMode;
    }

    public void setEventMode(boolean eventMode) {
        this.eventMode = eventMode;
    }
}
