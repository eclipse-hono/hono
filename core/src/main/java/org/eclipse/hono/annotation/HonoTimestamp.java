/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.annotation;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

/**
 * An annotation to indicate that an {@code Instant} valued field should be
 * de-/serialized from/to a string in <em>ISO-8601 extended offset date-time format</em>.
 * <p>
 * During deserialization, the date-time is normalized to UTC. For example,
 * the string <em>2019-09-15T15:23:10+02:00</em> will be deserialized
 * into an {@code Instant} which will then be serialized to <em>2019-09-15T13:23:10Z</em>.
 */
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonInclude(Include.NON_NULL)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@Documented
@JsonSerialize(using = Serializer.class)
@JsonDeserialize(using = Deserializer.class)
public @interface HonoTimestamp {
}

/**
 * A Jackson deserializer for strings in ISO 8601 extended offset date-time format.
 */
class Deserializer extends FromStringDeserializer<Instant> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new deserializer.
     */
    protected Deserializer() {
        super(Instant.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Instant _deserialize(final String value, final DeserializationContext ctxt) throws IOException {

        try {
            final OffsetDateTime dateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value, OffsetDateTime::from);
            return dateTime.toInstant();
        } catch (final DateTimeParseException e) {
            throw new IOException(e);
        }
    }
}

/**
 * A Jackson serializer that writes {@code Instant}s to strings in ISO 8601 instant format.
 */
class Serializer extends StdScalarSerializer<Instant> {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
    /**
     * Creates a new serializer.
     */
    protected Serializer() {
        super(Instant.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final Instant value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        gen.writeString(FORMATTER.format(OffsetDateTime.ofInstant(value, ZoneOffset.UTC)));
    }
}
