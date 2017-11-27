package org.eclipse.hono.vertx.example.base;

import java.util.concurrent.CountDownLatch;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

import static org.eclipse.hono.vertx.example.base.HonoExampleConstants.*;

/**
 * Example base class for consuming data from Hono.
 * <p>
 * By default, this class consumes telemetry data. This can be changed to event data by setting
 * {@link HonoConsumerBase#setEventMode(boolean)} to true.
 */
public class HonoConsumerBase {
    public static final String HONO_CLIENT_USER = "consumer@HONO";
    public static final String HONO_CLIENT_PASSWORD = "verysecret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;

    private boolean eventMode = false;

    public HonoConsumerBase() {
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HONO_AMQP_CONSUMER_HOST)
                        .port(HONO_AMQP_CONSUMER_PORT)
                        .user(HONO_CLIENT_USER)
                        .password(HONO_CLIENT_PASSWORD)
                        .trustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
    }

    protected void consumeData() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Future<MessageConsumer> consumerFuture = Future.future();

        consumerFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry consumer : " + result.cause());
            }
            latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();

        honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
            if (eventMode) {
                honoClient.createEventConsumer(TENANT_ID,
                        msg -> handleMessage(msg), consumerFuture.completer());
            } else {
                honoClient.createTelemetryConsumer(TENANT_ID,
                        msg -> handleMessage(msg), consumerFuture.completer());
            }
        },
                consumerFuture);

        latch.await();

        if (consumerFuture.succeeded()) {
            System.in.read();
        }
        vertx.close();
    }

    private void handleMessage(final Message msg) {
        final Section body = msg.getBody();
        if (!(body instanceof Data)) {
            return;
        }

        final String content = ((Data) msg.getBody()).getValue().toString();

        final String deviceId = MessageHelper.getDeviceId(msg);

        final StringBuilder sb = new StringBuilder("received message [device: ").
                append(deviceId).append(", content-type: ").append(msg.getContentType()).append(" ]: ").append(content);

        if (msg.getApplicationProperties() != null) {
            sb.append(" with application properties: ").append(msg.getApplicationProperties().getValue());
        }

        System.out.println(sb.toString());
    }

    public boolean isEventMode() {
        return eventMode;
    }

    public void setEventMode(boolean eventMode) {
        this.eventMode = eventMode;
    }
}
