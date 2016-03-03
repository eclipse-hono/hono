package org.eclipse.hono.adapter.rest;

import static org.apache.camel.component.amqp.AMQPComponent.amqp10Component;

import static org.eclipse.hono.adapter.rest.RestProtocolAdapter.HONO_IN_ONLY;
import static org.hamcrest.CoreMatchers.is;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class RestProtocolAdapterTest extends CamelTestSupport {

    private static InetSocketAddress brokerAddress;
    private static MessageConsumer consumer = new MessageConsumer();
    private static BrokerService broker;

    // Fixtures

    @BeforeClass
    public static void beforeClass() throws Exception {
        broker = new BrokerService();
        broker.setPersistent(false);
        TransportConnector connector = broker
                .addConnector("amqp://0.0.0.0:0?transport.transformer=jms&deliveryPeristent=false");
        broker.start();
        brokerAddress = connector.getServer().getSocketAddress();
    }

    @Before
    public void setup() {
        consumer.clear();
        consumer.setLatch(new CountDownLatch(1));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        String brokerUri = String.format("amqp://guest:guest@%s:%d", brokerAddress.getHostString(),
                brokerAddress.getPort());
        context.addComponent("amqp", amqp10Component(brokerUri));
        BinaryHttpRequestMapping requestMapping = new BinaryHttpRequestMapping();
        RestProtocolAdapter adapter = new RestProtocolAdapter(requestMapping, 8888);

        RouteBuilder serviceRoute = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("amqp:telemetry").setExchangePattern(ExchangePattern.InOnly).bean(consumer);
            }
        };

        return new RouteBuilder[]{adapter, serviceRoute};
    }

    // Tests

    @Test
    public void shouldHaveReceivedMessage() throws Exception {
        String json = "{\"temp\" : 15}";
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add(HONO_IN_ONLY, "true");
        HttpEntity<?> requestEntity = new HttpEntity<>(json, requestHeaders);
        new RestTemplate().exchange("http://localhost:8888/telemetry", HttpMethod.PUT, requestEntity, String.class);

        assertTrue(consumer.getLatch().await(2, TimeUnit.SECONDS));
        assertFalse(consumer.isEmpty());
        assertThat(consumer.get(0), is(json));
    }

    public static class MessageConsumer {
        private List<String> incomingMessages = new ArrayList<>();
        private CountDownLatch latch;

        public void setLatch(CountDownLatch newLatch) {
            this.latch = newLatch;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        public void process(Exchange exchange) {
            String message = exchange.getIn().getBody(String.class);
            addMessage(message);
        }

        public void addMessage(String message) {
            if (message != null) {
                incomingMessages.add(message);
                latch.countDown();
            }
        }

        public boolean isEmpty() {
            return incomingMessages.isEmpty();
        }

        public String get(int idx) {
            return incomingMessages.get(idx);
        }

        public void clear() {
            incomingMessages.clear();
        }
    }
}
