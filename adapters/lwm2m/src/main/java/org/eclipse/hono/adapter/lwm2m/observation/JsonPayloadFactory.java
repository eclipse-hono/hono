/**
 * Copyright (c) 2011-2015 Bosch Software Innovations GmbH, Germany. All rights reserved.
 */
package org.eclipse.hono.adapter.lwm2m.observation;

import java.nio.charset.StandardCharsets;

import org.eclipse.hono.adapter.lwm2m.Lwm2mResourceToObjectFunction;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mNodeVisitor;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Factory to create JSON message payload for telemetry data received for an observed resource.
 */
@Component
@Profile("step2")
public class JsonPayloadFactory implements TelemetryPayloadFactory {

    private static final String CONTENT_TYPE = "application/json;charset=utf-8";
    private final Lwm2mResourceToObjectFunction resourceConverter;

    @Autowired
    public JsonPayloadFactory(final Lwm2mResourceToObjectFunction lwm2mResourceToObjectFunction) {
        this.resourceConverter = lwm2mResourceToObjectFunction;
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    public byte[] getPayload(final LwM2mPath resourcePath, final ObjectModel objectModel, final LwM2mNode node) {

        HonoNodeVisitor visitor = new HonoNodeVisitor(resourcePath, objectModel);
        node.accept(visitor);
        return visitor.getMessage().encode().getBytes(StandardCharsets.UTF_8);
    }

    private final class HonoNodeVisitor implements LwM2mNodeVisitor {

        private static final String MEMBER_INSTANCE_ID = "id";
        private static final String MEMBER_INSTANCES_ARRAY = "instances";
        private LwM2mPath resourcePath;
        private ObjectModel objectModel;
        private JsonArray instanceArray;

        public HonoNodeVisitor(final LwM2mPath resourcePath, final ObjectModel objectModel) {

            this.resourcePath = resourcePath;
            this.objectModel = objectModel;
            this.instanceArray = new JsonArray();
        }

        public JsonObject getMessage() {
            return new JsonObject().put(MEMBER_INSTANCES_ARRAY, instanceArray);
        }

        @Override
        public void visit(final LwM2mResource resource) {
            JsonObject jsonInstance = newInstance(resourcePath.getObjectInstanceId());
            addResource(jsonInstance, resource);
            instanceArray.add(jsonInstance);
        }

        @Override
        public void visit(final LwM2mObjectInstance instance) {
            JsonObject jsonInstance = newInstance(instance.getId());
            instance.getResources().values().forEach(i -> addResource(jsonInstance, i));
            instanceArray.add(jsonInstance);
        }

        @Override
        public void visit(final LwM2mObject object) {
            object.getInstances().values().forEach(i -> visit(i));
        }

        private void addResource(final JsonObject instance, final LwM2mResource resource) {

            ResourceModel model = objectModel.resources.get(resource.getId());
            instance.put(model.name, resourceConverter.apply(resource));
        }

        private JsonObject newInstance(final int instanceId) {
            return new JsonObject().put(MEMBER_INSTANCE_ID, instanceId);
        }
    }
}
