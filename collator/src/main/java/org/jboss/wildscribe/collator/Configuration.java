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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"SameParameterValue", "unused"})
class Configuration {
    private final Map<String, String> properties;

    private Configuration() {
        this.properties = new HashMap<>();
        this.properties.putAll(System.getenv());
        final Properties sysProps = System.getProperties();
        for (String key : sysProps.stringPropertyNames()) {
            properties.put(key, sysProps.getProperty(key));
        }
    }

    static Configuration of(final Properties properties) {
        final Configuration config = new Configuration();
        for (String key : properties.stringPropertyNames()) {
            config.properties.put(key, properties.getProperty(key));
        }
        return config;
    }

    String get(final String key) {
        return properties.get(key);
    }

    String get(final String... keys) {
        for (String key : keys) {
            if (properties.containsKey(key)) {
                return properties.get(key);
            }
        }
        return null;
    }

    String getOrDefault(final String key, final String dft) {
        return properties.getOrDefault(key, dft);
    }

    String getOrDefault(final String key, final Supplier<String> dft) {
        final String value = properties.get(key);
        return value != null ? value : dft.get();
    }

    String[] getArray(final String key) {
        final String value = properties.get(key);
        if (value == null) {
            return new String[0];
        }
        return value.split(",");
    }
}
