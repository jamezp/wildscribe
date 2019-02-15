/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.wildscribe.collator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Version {

    private final String actualVersion;
    private final int major;
    private final int minor;
    private final int micro;
    private final String classifier;

    private Version(final String actualVersion, final int major, final int minor, final int micro, final String classifier) {
        this.actualVersion = actualVersion;
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.classifier = classifier;
    }

    static Version parse(final String version) {
        int major = 0;
        int minor = 0;
        int micro = 0;
        StringBuilder classifier = new StringBuilder();
        if (version != null) {
            final String[] parts = version.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                final String part = parts[i];
                if (i < 3 && isNumber(part)) {
                    final int value = Integer.parseInt(part);
                    switch (i) {
                        case 0:
                            major = value;
                            break;
                        case 1:
                            minor = value;
                            break;
                        case 2:
                            micro = value;
                            break;
                    }
                } else {
                    classifier.append(part);
                }
            }
        }
        return new Version(version, major, minor, micro, classifier.toString());
    }

    int getMajor() {
        return major;
    }

    int getMinor() {
        return minor;
    }

    int getMicro() {
        return micro;
    }

    @SuppressWarnings("unused")
    String getClassifier() {
        return classifier;
    }

    @Override
    public String toString() {
        return actualVersion;
    }

    private static boolean isNumber(final String value) {
        for (char c : value.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
