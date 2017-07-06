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
package org.apache.karaf.decanter.collector.jmx;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BeanHarvester {

    private final static Logger LOGGER = LoggerFactory.getLogger(BeanHarvester.class);

    private MBeanServerConnection connection;
    private String type;
    private String hostName;
    private String hostAddress;
    private String karafName;
    
    BeanHarvester(MBeanServerConnection connection, String type, String hostName, String karafName) throws UnknownHostException {
        this.connection = connection;
        this.type = type;
        this.hostName = hostName;
        if (hostName == null || hostName.isEmpty()) {
            this.karafName = karafName;
            this.hostAddress = InetAddress.getLocalHost().getHostAddress();
            this.hostName =InetAddress.getLocalHost().getHostName();
        } else {
            this.karafName = null;
            this.hostAddress = null;
            this.hostName = hostName;
        }
    }

    Map<String, Object> harvestBean(ObjectName name) throws Exception {
        LOGGER.debug("Harvesting {}", name);
        MBeanAttributeInfo[] attributes = connection.getMBeanInfo(name).getAttributes();
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("ObjectName", name.toString());
        if (this.karafName != null) {
            data.put("karafName", this.karafName);
        }
        if (this.hostAddress != null) {
            data.put("hostAddress", this.hostAddress);
        }
        data.put("hostName", hostName);

        for (MBeanAttributeInfo attribute : attributes) {
            try {
                Object attributeObject = connection.getAttribute(name, attribute.getName());
                if (attributeObject instanceof String) {
                    data.put(attribute.getName(), (String) attributeObject);
                } else if (attributeObject instanceof ObjectName) {
                    data.put(attribute.getName(), ((ObjectName) attributeObject).toString());
                } else if (attributeObject instanceof CompositeDataSupport || attributeObject instanceof CompositeData) {
                    CompositeData cds = (CompositeData) attributeObject;
                    CompositeType compositeType = cds.getCompositeType();
                    Set<String> keySet = compositeType.keySet();
                    Map<String, Object> composite = new HashMap<String, Object>();
                    for (String key : keySet) {
                        Object cdsObject = cds.get(key);
                        composite.put(key, cdsObject);
                    }
                    data.put(attribute.getName(), composite);
                } else if (attributeObject instanceof Long
                        || attributeObject instanceof Integer
                        || attributeObject instanceof Boolean
                        || attributeObject instanceof Float
                        || attributeObject instanceof Double) {
                    data.put(attribute.getName(), attributeObject);
                } else if (attributeObject instanceof TabularDataSupport || attributeObject instanceof TabularData) {
                    TabularData tds = (TabularData) attributeObject;
                    TabularType tabularType = tds.getTabularType();
                    CompositeType compositeType = tabularType.getRowType();
                    Collection<?> values = tds.values();
                    ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
                    for (Object value : values) {
                        CompositeDataSupport cds = (CompositeDataSupport) value;
                        Set<String> keySet = compositeType.keySet();
                        Map<String, Object> composite = new HashMap<String, Object>();
                        list.add(composite);
                        for (String key : keySet) {
                            Object cdsObject = cds.get(key);
                            composite.put(key, cdsObject);
                        }
                    }
                    data.put(attribute.getName(), list);
                } else if (attributeObject instanceof Object[]) {
                    data.put(attribute.getName(), (Object[]) attributeObject);
                } else if (attributeObject instanceof long[]) {
                    data.put(attribute.getName(), (long[]) attributeObject);
                } else if (attributeObject instanceof String[]) {
                    data.put(attribute.getName(), (String[]) attributeObject);
                } else if (attributeObject instanceof int[]) {
                    data.put(attribute.getName(), (int[]) attributeObject);
                } else {
                    data.put(attribute.getName(), attributeObject.toString());
                }
            } catch (SecurityException se) {
                LOGGER.error("SecurityException: ", se);
            } catch (Exception e) {
                LOGGER.debug("Could not read attribute " + name.toString() + " " + attribute.getName());
            }

        }
        return data;
    }
}
