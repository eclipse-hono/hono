/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.device;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;

/**
 * A utility class for handling template used for generating device identifier during auto-provisioning.
 */
public final class IdentityTemplate {

    private static final String QUOTED_PLACEHOLDER_SUBJECT_DN = Pattern
            .quote(RegistryManagementConstants.PLACEHOLDER_SUBJECT_DN);
    private final String template;

    /**
     * Creates a new identity template.
     *
     * @param template The identity template.
     * @throws NullPointerException if template is {@code null}.
     */
    public IdentityTemplate(final String template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    /**
     * An enum defining various Subject DN attributes that are supported in the device-id template.
     */
    private enum Attribute {

        CN("Common Name", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN),
        OU("Organizational Unit Name", RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU),
        O("Organization Name", RegistryManagementConstants.PLACEHOLDER_SUBJECT_O);

        private final String name;
        private final String placeHolder;

        Attribute(final String name, final String placeHolder) {
            Objects.requireNonNull(name, "attribute name must not be null");
            Objects.requireNonNull(placeHolder, "attribute placeholder must not be null");
            this.name = name;
            this.placeHolder = placeHolder;
        }

        /**
         * Gets the attribute name.
         *
         * @return the attribute name.
         */
        String getName() {
            return name;
        }

        /**
         * Gets the placeholder corresponding to the attribute.
         *
         * @return the placeholder.
         */
        String getPlaceHolder() {
            return placeHolder;
        }

        /**
         * Extracts the attribute value from the given list of RDNs (Representation of Distinguished Names).
         * <p>
         * If the RDNs list contains multiple occurrences of the same attribute, the value
         * of the first occurrence is returned.
         *
         * @param rdns The list of RDNs.
         * @return The attribute value or {@code null} if the attribute is not available in the RDNs list.
         * @throws NullPointerException if rdns is {@code null}.
         */
        String getValue(final List<Rdn> rdns) {
            Objects.requireNonNull(rdns, "rdns list must not be null");

            return rdns
                    .stream()
                    .filter(rdn -> this.toString().equalsIgnoreCase(rdn.getType()))
                    .findFirst()
                    .map(Rdn::getValue)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElse(null);
        }
    }

    /**
     * Applies attribute values from the given subject DN to the template.
     * <p>
     * The following placeholders are supported.
     * <ul>
     * <li>{@value RegistryManagementConstants#PLACEHOLDER_SUBJECT_DN} for <em>Subject Distinguished Name (DN)</em></li>
     * <li>{@value RegistryManagementConstants#PLACEHOLDER_SUBJECT_CN} for <em>Common Name (CN)</em></li>
     * <li>{@value RegistryManagementConstants#PLACEHOLDER_SUBJECT_OU} for <em>Organizational Unit Name (OU)</em></li>
     * <li>{@value RegistryManagementConstants#PLACEHOLDER_SUBJECT_O} for <em>Organization Name (O)</em></li>
     * </ul>
     *
     * @param subjectDN The subject DN.
     * @return The filled template.
     * @throws IllegalArgumentException if the subject DN is not valid or any of the attributes
     *                                  configured in the template are not present in the subject DN.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public String apply(final String subjectDN) {
        Objects.requireNonNull(subjectDN, "subjectDN must not be null");

        try {
            final List<Rdn> rdns = new LdapName(subjectDN).getRdns();
            String result = template.replaceAll(QUOTED_PLACEHOLDER_SUBJECT_DN, subjectDN);
            for (final Attribute attribute : Attribute.values()) {
                result = applyAttribute(attribute, result, rdns);
            }
            return result;
        } catch (final InvalidNameException e) {
            throw new IllegalArgumentException(String.format("subject DN [%s] is not valid", subjectDN));
        }
    }

    private static String applyAttribute(final Attribute attribute, final String template,
            final List<Rdn> rdns) {
        if (template.contains(attribute.getPlaceHolder())) {
            final String attributeValue = attribute.getValue(rdns);
            if (Strings.isNullOrEmpty(attributeValue)) {
                throw new IllegalArgumentException(
                        String.format(
                                "error filling template [%s] as [%s] is missing in client certificate's Subject DN",
                                template, attribute.getName()));
            }
            return template.replaceAll(Pattern.quote(attribute.getPlaceHolder()), attributeValue);
        }
        return template;
    }
}
