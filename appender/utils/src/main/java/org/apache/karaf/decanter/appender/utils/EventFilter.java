/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.appender.utils;

import org.osgi.service.event.Event;

import java.util.Dictionary;

public class EventFilter {

    public static final String PROPERTY_NAME_EXCLUDE_CONFIG = "event.property.name.exclude";
    public static final String PROPERTY_NAME_INCLUDE_CONFIG = "event.property.name.include";
    public static final String PROPERTY_VALUE_EXCLUDE_CONFIG = "event.property.value.exclude";
    public static final String PROPERTY_VALUE_INCLUDE_CONFIG = "event.property.value.include";

    public static boolean match(Event event, Dictionary<String, Object> config) {
        if (config == null) {
            return true;
        }

        String nameExcludeRegex = (config.get(PROPERTY_NAME_EXCLUDE_CONFIG) != null) ? (String) config.get(PROPERTY_NAME_EXCLUDE_CONFIG) : null;
        String nameIncludeRegex = (config.get(PROPERTY_NAME_INCLUDE_CONFIG) != null) ? (String) config.get(PROPERTY_NAME_INCLUDE_CONFIG) : null;
        String valueExcludeRegex = (config.get(PROPERTY_VALUE_EXCLUDE_CONFIG) != null) ? (String) config.get(PROPERTY_VALUE_EXCLUDE_CONFIG) : null;
        String valueIncludeRegex = (config.get(PROPERTY_VALUE_INCLUDE_CONFIG) != null) ? (String) config.get(PROPERTY_VALUE_INCLUDE_CONFIG) : null;

        for (String name : event.getPropertyNames()) {
            if (nameExcludeRegex != null && name.matches(nameExcludeRegex)) {
                return false;
            }

            if (nameIncludeRegex != null && name.matches(nameIncludeRegex)) {
                return true;
            }

            if (event.getProperty(name) != null && event.getProperty(name) instanceof String) {
                if (valueExcludeRegex != null && ((String) event.getProperty(name)).matches(valueExcludeRegex)) {
                    return false;
                }
                if (valueIncludeRegex != null && ((String) event.getProperty(name)).matches(valueIncludeRegex)) {
                    return true;
                }
            }

        }
        return true;
    }

}
