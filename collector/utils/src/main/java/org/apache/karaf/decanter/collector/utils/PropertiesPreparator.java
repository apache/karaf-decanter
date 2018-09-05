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
package org.apache.karaf.decanter.collector.utils;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

public class PropertiesPreparator {

    private final static String FIELDS_ADD = "fields.add.";
    private final static String FIELDS_RENAME = "fields.rename.";
    private final static String FIELDS_REMOVE = "fields.remove.";

    private static SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    /**
     * Prepare the data sent to the dispatcher using default properties and provided custom fields.
     *
     * @param data Data container sent to the dispatcher by the collector.
     * @param properties Custom properties included in the data.
     */
    public static void prepare(Map<String, Object> data, Dictionary<String, Object> properties) throws Exception {
        // add the karaf instance name
        String karafName = System.getProperty("karaf.name");
        if (karafName != null) {
            data.put("karafName", karafName);
        }

        // add the network details
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());

        // custom fields
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith(FIELDS_ADD)) {
                if ("UUID".equals(properties.get(key).toString().trim())) {
                    String uuid = UUID.randomUUID().toString();
                    data.put(key.substring(FIELDS_ADD.length()), uuid);
                } else if ("TIMESTAMP".equals(properties.get(key).toString().trim())) {
                    Date date = new Date();
                    data.put(key.substring(FIELDS_ADD.length()), tsFormat.format(date));
                } else {
                    data.put(key.substring(FIELDS_ADD.length()), properties.get(key));
                }
            } else if (key.startsWith(FIELDS_RENAME)) {
                if (data.containsKey(key.substring(FIELDS_RENAME.length()))) {
                    Object value = data.get(key.substring(FIELDS_RENAME.length()));
                    data.remove(key.substring(FIELDS_RENAME.length()));
                    data.put(properties.get(key).toString().trim(), value);
                }
            } else if (key.startsWith(FIELDS_REMOVE)) {
                if (data.containsKey(key.substring(FIELDS_REMOVE.length()))) {
                    data.remove(key.substring(FIELDS_REMOVE.length()));
                }
            } else {
                data.put(key, properties.get(key));
            }
        }
    }

}
