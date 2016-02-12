/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.client.api.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TopicAcl implements Serializable {
    private final String      authSubject;
    private final Permissions permissions;

    public TopicAcl(final String authSubject, final Permissions permissions) {
        this.authSubject = authSubject;
        this.permissions = permissions;
    }

    public String getAuthSubject() {
        return authSubject;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(this);
            oos.flush();
            byte[] bytes = os.toByteArray();
            return bytes;
        }
    }

    public static TopicAcl fromBytes(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(is)) {
            Object readObject = ois.readObject();
            if (readObject instanceof TopicAcl) {
                return (TopicAcl) readObject;
            } else {
                // TODO refine
                return null;
            }
        }
    }

    @Override
    public String toString()
    {
        return "TopicAcl{" +
           "authSubject='" + authSubject + '\'' +
           ", permissions=" + permissions +
           '}';
    }

    @Override public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final TopicAcl topicAcl = (TopicAcl) o;

        if (authSubject != null ? !authSubject.equals(topicAcl.authSubject) : topicAcl.authSubject != null)
            return false;
        return permissions != null ? permissions.equals(topicAcl.permissions) : topicAcl.permissions == null;

    }

    @Override public int hashCode() {
        int result = authSubject != null ? authSubject.hashCode() : 0;
        result = 31 * result + (permissions != null ? permissions.hashCode() : 0);
        return result;
    }
}
