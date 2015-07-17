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

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter JMX Pooling Collector
 */
public class JmxCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(JmxCollector.class);

    private String type;
    private String url;
    private String username;
    private String password;
    private String objectName;
    private EventAdmin eventAdmin;

    public JmxCollector(String type, String url, String username, String password, String objectName, EventAdmin eventAdmin) {
        this.type = type;
        this.url = url;
        this.username = username;
        this.password = password;
        this.eventAdmin = eventAdmin;
        this.objectName = objectName;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter JMX Collector starts harvesting {}...", type);

        JMXConnector connector = null;
        MBeanServerConnection connection = null;

        String host = null;
        if (url == null || url.equalsIgnoreCase("local")) {
            LOGGER.debug("Harvesting local MBeanServer ({})...", type);
            connection = ManagementFactory.getPlatformMBeanServer();
        } else {
            try {
                JMXServiceURL jmxServiceURL = new JMXServiceURL(url);
                ArrayList<String> list = new ArrayList<>();
                if (username != null) {
                    list.add(username);
                }
                if (password != null) {
                    list.add(password);
                }
                HashMap env = new HashMap();
                String[] credentials = list.toArray(new String[list.size()]);
                env.put(JMXConnector.CREDENTIALS, credentials);
                if (credentials.length > 0) {
                    connector = JMXConnectorFactory.connect(jmxServiceURL, env);
                } else {
                    connector = JMXConnectorFactory.connect(jmxServiceURL);
                }
                connection = connector.getMBeanServerConnection();
                host = jmxServiceURL.toString();
            } catch (Exception e) {
                LOGGER.error("Can't connect to MBeanServer {} ({})", url, type, e);
            }
        }

        if (connection != null) {
            try {
                Set<ObjectName> names;
                if (objectName != null) {
                    names = connection.queryNames(new ObjectName(objectName), null);
                } else {
                    names = connection.queryNames(null, null);
                }
                for (ObjectName name : names) {
                    try {
                        Map<String, Object> data = harvestBean(connection, name, type, host);
                        Event event = new Event("decanter/collect/jmx/" + type + "/" + getTopic(name), data);
                        eventAdmin.postEvent(event);
                    } catch (Exception e) {
                        LOGGER.warn("Can't read MBean {} ({})", name, type, e);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Can't query object name from {} ({}) {}", url, type, objectName);
            }
        } else {
            LOGGER.debug("MBean connection is null, nothing to do");
        }

        if (connector != null) {
            try {
                connector.close();
            } catch (Exception e) {
                LOGGER.trace("Can't close JMX connector", e);
            }
        }

        LOGGER.debug("Karaf Decanter JMX Collector harvesting {} done", type);
    }

    private String getTopic(ObjectName name) {
        return name.getDomain().replace(".", "/").replace(" ", "_");
    }

    Map<String, Object> harvestBean(MBeanServerConnection connection, ObjectName name, String type, String hostName) throws Exception {
        MBeanAttributeInfo[] attributes = connection.getMBeanInfo(name).getAttributes();
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("ObjectName", name.toString());

        if (hostName == null || hostName.isEmpty()) {
            data.put("karafName", System.getProperty("karaf.name"));
            data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
            data.put("hostName", InetAddress.getLocalHost().getHostName());
        } else {
            data.put("hostName", hostName);
        }

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
                    Collection values = tds.values();
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
