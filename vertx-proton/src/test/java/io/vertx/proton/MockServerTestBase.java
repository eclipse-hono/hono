/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.proton;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import org.junit.After;
import org.junit.Before;

import java.util.concurrent.atomic.AtomicLong;

abstract public class MockServerTestBase {

    protected Vertx vertx;
    protected MockServer server;

    @Before
    public void setup() {
        try {
            // Create the Vert.x instance
            vertx = Vertx.vertx();
            server = new MockServer(vertx);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void tearDown() {
        server.close();
        vertx.close();
    }

    protected void benchmark(long timeout, String name, Handler<AtomicLong> work, Runnable done) {
        AtomicLong counter = new AtomicLong();
        AtomicLong startTime = new AtomicLong();
        AtomicLong intervalTime = new AtomicLong();
        AtomicLong intervalCounter = new AtomicLong();
        System.out.println("Benchmarking " + name + " rate ...");
        long periodic = vertx.setPeriodic(1000, t -> {
            long now = System.currentTimeMillis();
            double duration = (now - intervalTime.getAndSet(now)) / 1000.0d;
            long sent = counter.get() - intervalCounter.get();
            System.out.println(String.format("... %s rate: %,.2f", name, (sent / duration)));
            intervalCounter.addAndGet(sent);
        });
        vertx.setTimer(timeout, t -> {
            vertx.cancelTimer(periodic);
            double duration = (System.currentTimeMillis() - startTime.get()) / 1000.0d;
            long sent = counter.get();
            System.out.println(String.format("Final %s rate: %,.2f", name, (sent / duration)));
            done.run();
        });
        startTime.set(System.currentTimeMillis());
        intervalTime.set(startTime.get());
        work.handle(counter);
    }

    protected void connect(TestContext context, Handler<ProtonConnection> handler) {
        ProtonClient client = ProtonClient.create(vertx);
        client.connect("localhost", server.actualPort(), res -> {
            context.assertTrue(res.succeeded());
            handler.handle(res.result());
        });
    }
}
