/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;

/**
 * An annotation to indicate that a value should be serialized/de-serialized in the format expected by Hono.
 */
@Retention(RUNTIME)
@JacksonAnnotationsInside
@JsonInclude(NON_NULL)
@Target(FIELD)
@Documented
@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[XXX]", timezone = "UTC")
@JsonSerialize(using = InstantSerializer.class)
@JsonDeserialize(using = HonoInstantDeserializer.class)
public @interface HonoTimestamp {
}

/**
 * A deserializer for Hono's format of {@link Instant}s.
 */
class HonoInstantDeserializer extends InstantDeserializer<Instant> {

    private static final long serialVersionUID = 1L;

    HonoInstantDeserializer() {
        super(Instant.class, DateTimeFormatter.ISO_INSTANT,
                Instant::from,
                a -> Instant.ofEpochMilli(a.value),
                a -> Instant.ofEpochSecond(a.integer, a.fraction),
                null,
                true);
    }

}
