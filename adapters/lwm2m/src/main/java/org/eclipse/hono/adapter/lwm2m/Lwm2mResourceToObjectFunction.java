/**
 * Copyright (c) 2011-2015 Bosch Software Innovations GmbH, Germany. All rights reserved.
 */
package org.eclipse.hono.adapter.lwm2m;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonArray;

/**
 * A function that maps a {@link LwM2mResource}'s value to an object suitable for inclusion in a Vert.x {@code JsonObject}.
 */
@Component
public class Lwm2mResourceToObjectFunction implements Function<LwM2mResource, Object> {
    private static final String UNKNOWN_TYPE = "unknown type ";
    private final Map<ResourceModel.Type, Function<Object, Object>> converters = new EnumMap<>(
            ResourceModel.Type.class);

    @PostConstruct
    void init() {
        converters.put(ResourceModel.Type.STRING, value -> (String) value);
        converters.put(ResourceModel.Type.INTEGER, value -> (Long) value);
        converters.put(ResourceModel.Type.FLOAT, value -> (Double) value);
        converters.put(ResourceModel.Type.BOOLEAN, value -> (Boolean) value);
        converters.put(ResourceModel.Type.TIME, value -> timeConverter((Date) value));
        converters.put(ResourceModel.Type.OPAQUE, value -> (byte[]) value);
    }

    @Override
    public Object apply(final LwM2mResource t) {
        return convertToSingleOrMultipleValue(t);

    }

    private Object convertToSingleOrMultipleValue(final LwM2mResource lwm2mResource) {
        final Function<Object, Object> converter = getConverter(lwm2mResource.getType());
        if (!lwm2mResource.isMultiInstances()) {
            return converter.apply(lwm2mResource.getValue());
        } else {
            final JsonArray result = new JsonArray();
            @SuppressWarnings("unchecked")
            final Map<Integer, Object> values = (Map<Integer, Object>) lwm2mResource.getValues();
            for (final Map.Entry<Integer, Object> entry : values.entrySet()) {
                result.add(converter.apply(entry.getValue()));
            }
            return result;
        }
    }

    private Function<Object, Object> getConverter(final ResourceModel.Type dataType) {
        return Optional.ofNullable(converters.get(dataType))
                .orElseThrow(() -> new IllegalStateException(UNKNOWN_TYPE + dataType));
    }

    /**
     * case Time: Unix Time. A signed integer representing the number of seconds
     * since Jan 1st, 1970 in the UTC time zone.
     */
    private Long timeConverter(final Date value) {
        return value.getTime() / 1000L;
    }
}
