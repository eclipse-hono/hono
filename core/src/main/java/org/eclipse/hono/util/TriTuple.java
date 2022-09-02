/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

/**
 * A tuple of three values of arbitrary type.
 *
 * @param <A> The type of the first value.
 * @param <B> The type of the second value.
 * @param <C> The type of the third value.
 */
public final class TriTuple<A, B, C> {

    private final A one;
    private final B two;
    private final C three;

    private String stringRep;

    private TriTuple(final A one, final B two, final C three) {

        if (one == null && two == null && three == null) {
            throw new IllegalArgumentException("at least one argument must be non-null");
        }
        this.one = one;
        this.two = two;
        this.three = three;
    }

    /**
     * Creates a new tuple for values of arbitrary type.
     *
     * @param one First value.
     * @param two Second value.
     * @param three Third value.
     * @param <A> The type of the first value.
     * @param <B> The type of the second value.
     * @param <C> The type of the third value.
     * @return The tuple.
     * @throws IllegalArgumentException if all values are {@code null}.
     */
    public static <A, B, C>  TriTuple<A, B, C> of(final A one, final B two, final C three) {
        return new TriTuple<>(one, two, three);
    }

    /**
     * Gets this tuple's first value.
     *
     * @return The value or {@code null} if not set.
     */
    public A one() {
        return one;
    }

    /**
     * Gets this tuple's second value.
     *
     * @return The value or {@code null} if not set.
     */
    public B two() {
        return two;
    }

    /**
     * Gets this tuple's third value.
     *
     * @return The value or {@code null} if not set.
     */
    public C three() {
        return three;
    }

    @Override
    public String toString() {
        if (stringRep == null) {
            stringRep = String.format("TriTuple[one: %s, two: %s, three: %s]", one, two, three);
        }
        return stringRep;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (one == null ? 0 : one.hashCode());
        result = prime * result + (three == null ? 0 : three.hashCode());
        result = prime * result + (two == null ? 0 : two.hashCode());
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TriTuple other = (TriTuple) obj;
        if (one == null) {
            if (other.one != null) {
                return false;
            }
        } else if (!one.equals(other.one)) {
            return false;
        }
        if (three == null) {
            if (other.three != null) {
                return false;
            }
        } else if (!three.equals(other.three)) {
            return false;
        }
        if (two == null) {
            if (other.two != null) {
                return false;
            }
        } else if (!two.equals(other.two)) {
            return false;
        }
        return true;
    }
}
