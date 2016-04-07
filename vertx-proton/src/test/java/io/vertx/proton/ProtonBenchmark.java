/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.qpid.proton.message.Message;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.vertx.proton.ProtonHelper.message;
import static io.vertx.proton.ProtonHelper.tag;

@RunWith(VertxUnitRunner.class)
public class ProtonBenchmark extends MockServerTestBase {

    static final long BENCHMARK_DURATION = 5000;

    public static void main(String[] args) {
        ProtonBenchmark benchmark = new ProtonBenchmark();
        TestSuite.create("benchmark")
            .before(x->benchmark.setup())
            .test("benchmark", x -> benchmark.benchmarkAtMostOnceSendThroughput(x))
            .after(x -> benchmark.tearDown())
            .run(new TestOptions().setTimeout(BENCHMARK_DURATION+10000));
    }

    @Test
    public void benchmarkAtLeastOnceSendThroughput(TestContext context) {
        server.setProducerCredits(1000);

        Async async = context.async();
        connect(context, connection ->
        {
            connection.open();

            ProtonSender sender =
                connection.createSender(MockServer.Addresses.drop.toString())
                    .setQoS(ProtonQoS.AT_LEAST_ONCE)
                    .open();

            String name = "At Least Once Send Throughput";
            byte[] tag = tag("m1");
            Message message = message("drop", "Hello World");

            benchmark(BENCHMARK_DURATION, name, counter -> {
                sender.sendQueueDrainHandler(s -> {
                    while (!sender.sendQueueFull()) {
                        sender.send(tag, message, d -> {
                            if(d.remotelySettled()) {
                                counter.incrementAndGet();
                            }
                        });
                    }
                });
            }, () -> {
                connection.disconnect();
                async.complete();
            });
        });
    }

    @Test
    public void benchmarkAtMostOnceSendThroughput(TestContext context) {
        server.setProducerCredits(5000);

        Async async = context.async();
        connect(context, connection ->
        {
            connection.open();

            ProtonSender sender =
                connection.createSender(MockServer.Addresses.drop.toString())
                    .setQoS(ProtonQoS.AT_MOST_ONCE)
                    .open();

            String name = "At Most Once Send Throughput";
            byte[] tag = tag("m1");
            Message message = message("drop", "Hello World");

            benchmark(BENCHMARK_DURATION, name, counter -> {
                sender.sendQueueDrainHandler(s -> {
                    while (!sender.sendQueueFull()) {
                        sender.send(tag, message);
                        counter.incrementAndGet();
                    }
                });
            }, () -> {
                connection.disconnect();
                async.complete();
            });
        });
    }

    @Test
    public void benchmarkRequestResponse(TestContext context) {
        int credits = 10;
        server.setProducerCredits(credits);

        Async async = context.async();
        connect(context, connection ->
        {
            connection.open();

            ProtonSender sender = connection.createSender(MockServer.Addresses.echo.toString()).open();

            byte[] tag = tag("m1");
            Message message = message("echo", "Hello World");

            benchmark(BENCHMARK_DURATION, "Request Response Throughput", counter -> {

                connection.createReceiver(MockServer.Addresses.echo.toString())
                    .handler((d, m)->{
                        counter.incrementAndGet();
                    })
                    .flow(credits)
                    .open();

                sender.sendQueueDrainHandler(s -> {
                    while (!sender.sendQueueFull()) {
                        sender.send(tag, message);
                    }
                });
            }, () -> {
                connection.disconnect();
                async.complete();
            });
        });
    }


}
