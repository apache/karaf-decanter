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
import java.util.*;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter JMX Pooling Collector
 */
@Component(
    name = "org.apache.karaf.decanter.collector.jmx",
    immediate = true,
    property = "decanter.collector.name=jmx"
)
public class JmxCollector implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(JmxCollector.class);

    private String type;
    private String url;
    private String username;
    private String password;
    private String objectName;
    private EventAdmin eventAdmin;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        properties = context.getProperties();
        String type = getProperty(properties, "type", "jmx-local");
        String url = getProperty(properties, "url", "local");
        String username = getProperty(properties, "username", null);
        String password = getProperty(properties, "password", null);
        String objectName = getProperty(properties, "object.name", null);
        Dictionary<String, String> serviceProperties = new Hashtable<String, String>();
        serviceProperties.put("decanter.collector.name", type);
        this.type = type;
        this.url = url;
        this.username = username;
        this.password = password;
        this.objectName = objectName;
    }
    
    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
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
                connector = JMXConnectorFactory.connect(jmxServiceURL, getEnv());
                connection = connector.getMBeanServerConnection();
                host = jmxServiceURL.toString();
            } catch (Exception e) {
                LOGGER.error("Can't connect to MBeanServer {} ({})", url, type, e);
            }
        }

        if (connection == null) {
            LOGGER.debug("MBean connection is null, nothing to do");
            return;
        }

        try {
            String karafName = System.getProperty("karaf.name");
            BeanHarvester harvester = new BeanHarvester(connection, type, host, karafName);
            Set<ObjectName> names = connection.queryNames(getObjectName(objectName), null);
            for (ObjectName name : names) {
                try {
                    Map<String, Object> data = harvester.harvestBean(name);
                    addUserProperties(data);
                    Event event = new Event("decanter/collect/jmx/" + type + "/" + getTopic(name), data);
                    eventAdmin.postEvent(event);
                } catch (Exception e) {
                    LOGGER.warn("Can't read MBean {} ({})", name, type, e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't query object name from {} ({}) {}", url, type, objectName);
        }

        try {
            connector.close();
        } catch (Exception e) {
            LOGGER.trace("Can't close JMX connector", e);
        }

        LOGGER.debug("Karaf Decanter JMX Collector harvesting {} done", type);
    }

    private Map<String, Object> getEnv() {
        Map<String, Object> env = new HashMap<>();
        if (username != null && password != null) {
            env.put(JMXConnector.CREDENTIALS, new String[] { username, password });
        }            
        return env;
    }

    private void addUserProperties(Map<String, Object> data) {
        if (properties != null) {
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String property = keys.nextElement();
                data.put(property, properties.get(property));
            }
        }
    }

    private ObjectName getObjectName(String objectName) throws MalformedObjectNameException {
        return objectName == null ? null : new ObjectName(objectName);
    }

    private String getTopic(ObjectName name) {
        return name.getDomain().replace(".", "/").replace(" ", "_");
    }

    @Reference
    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }
}
