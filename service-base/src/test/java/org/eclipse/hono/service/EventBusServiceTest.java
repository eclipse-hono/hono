package org.eclipse.hono.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.EventBusMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventBusServiceTest {

    @Test
    public void getRequestPayloadHandlesInvalidClass() {
        JsonObject device = new JsonObject().put("device-id", "someValue").put("enabled", "notABoolean");

        EventBusService e = new EventBusService<Object>(){

            @Override
            public void setConfig(Object configuration) {

            }

            @Override
            protected String getEventBusAddress() {
                return null;
            }

            @Override
            protected Future<EventBusMessage> processRequest(EventBusMessage request) {
                return null;
            }
        };

        JsonObject result = e.getRequestPayload(device);
        assertEquals(result.getBoolean("enabled"), true);
    }
}
