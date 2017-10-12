/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.device;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;

/**
 * A base class providing utility methods for verifying credentials.
 *
 */
public abstract class AbstractDeviceCredentials implements DeviceCredentials {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public final boolean validate(final CredentialsObject credentialsOnRecord) {

        Objects.requireNonNull(credentialsOnRecord);
        if (!getAuthId().equals(credentialsOnRecord.getAuthId())) {
            return false;
        } else if (!getType().equals(credentialsOnRecord.getType())) {
            return false;
        } else if (!credentialsOnRecord.getEnabled()) {
            return false;
        } else {

            final List<Map<String, String>> secrets = credentialsOnRecord.getSecrets();

            if (secrets == null) {
                throw new IllegalArgumentException(String.format("credentials not validated - mandatory field %s is null", CredentialsConstants.FIELD_SECRETS));
            } else {
                return validate(secrets);
            }
        }
    }

    private boolean validate(final List<Map<String, String>> secretsOnRecord) {

        for (Map<String, String> candidateSecret : secretsOnRecord) {
            if (isInValidityPeriod(candidateSecret, Instant.now()) && matchesCredentials(candidateSecret)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInValidityPeriod(final Map<String, String> secret, final Instant instant) {

        try {
            Instant notBefore = getInstant(secret, CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
            Instant notAfter = getInstant(secret, CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
            return (notBefore == null || instant.isAfter(notBefore)) && (notAfter == null || instant.isBefore(notAfter));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Instant getInstant(final Map<String, String> secret, final String propertyName) {
        String timestamp = Objects.requireNonNull(secret).get(Objects.requireNonNull(propertyName));
        if (timestamp == null) {
            return null;
        } else {
            try {
                OffsetDateTime dateTime = DATE_TIME_FORMATTER.parse(timestamp, OffsetDateTime::from);
                return dateTime == null ? null : dateTime.toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("illegal timestamp format");
            }
        }
    }

    /**
     * Checks if the credentials provided by the device match a secret on record for the device.
     * 
     * @param candidateSecret The secret to match against.
     * @return {@code true} if the credentials match.
     */
    public abstract boolean matchesCredentials(final Map<String, String> candidateSecret);
}
