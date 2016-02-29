package org.eclipse.hono.adapter.rest;

import com.google.common.truth.Truth;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;

import static org.apache.camel.component.amqp.AMQPComponent.amqp10Component;

public class RestProtocolAdapterTest extends CamelTestSupport {

    // Fixtures

    @BeforeClass
    public static void beforeClass() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);
        broker.addConnector("amqp://0.0.0.0:9999?transport.transformer=jms");
        broker.start();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        context.addComponent("amqp", amqp10Component("amqp://guest:guest@localhost:9999"));

        BinaryHttpRequestMapping requestMapping = new BinaryHttpRequestMapping();
        RestProtocolAdapter adapter = new RestProtocolAdapter(requestMapping, 8888);

        RouteBuilder serviceRoute = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("amqp:hello").setBody().constant("hello service");
                from("amqp:echo").log("Executing echo service.");
                from("amqp:echoheader").setBody().header("foo");
            }
        };

        return new RouteBuilder[]{adapter, serviceRoute};
    }

    // Tests

    @Test
    public void shouldReceiveResponseFromService() throws IOException {
        String response = IOUtils.toString(new URL("http://localhost:8888/hello"));
        Truth.assertThat(response).isEqualTo("hello service");
    }

    @Test
    public void shouldReceiveBodyFromService() throws IOException {
        String response = new RestTemplate().postForObject("http://localhost:8888/echo", "foo", String.class);
        Truth.assertThat(response).isEqualTo("foo");
    }

    @Test
    public void shouldReceiveHeader() throws IOException {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("foo", "bar");
        HttpEntity<?> requestEntity = new HttpEntity<>(requestHeaders);
        String response = new RestTemplate().exchange("http://localhost:8888/echoheader", HttpMethod.GET, requestEntity, String.class).getBody();
        Truth.assertThat(response).isEqualTo("bar");
    }

}
