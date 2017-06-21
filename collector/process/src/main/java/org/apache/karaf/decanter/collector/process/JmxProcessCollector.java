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
package org.apache.karaf.decanter.collector.process;

import java.io.File;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Decanter JMX Local Process Pooling Collector
 */
@Component(
        name = "org.apache.karaf.decanter.collector.process",
        immediate = true,
        property = {"decanter.collector.name=process",
                "scheduler.period:Long=10",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-process"}
)
public class JmxProcessCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(JmxProcessCollector.class);

    private String type;
    private String process;
    private String objectName;
    private EventAdmin eventAdmin;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        properties = context.getProperties();
        String type = getProperty(properties, "type", "process-jmx");
        String process = getProperty(properties, "process", null);
        String objectName = getProperty(properties, "object.name", null);
        Dictionary<String, String> serviceProperties = new Hashtable<String, String>();
        serviceProperties.put("decanter.collector.name", type);
        this.type = type;
        this.process = process;
        this.objectName = objectName;
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        LOGGER.debug("Karaf Decanter JMX Local Process Collector starts harvesting {}...", type);

        JMXConnector connector = null;
        MBeanServerConnection connection = null;

        String host = null;
        if (process == null) {
            LOGGER.error("Can't connect to MBeanServer on local process, as not declared");
            return;
        } else {
            try {
                for (final VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
                    if (descriptor.displayName().contains(process)) {
                        VirtualMachine vm = VirtualMachine.attach(descriptor.id());
                        String connectorAddr = vm.getAgentProperties()
                                .getProperty("com.sun.management.jmxremote.localConnectorAddress");
                        if (connectorAddr == null) {
                            String agent = vm.getSystemProperties().getProperty("java.home") + File.separator + "lib"
                                    + File.separator + "management-agent.jar";
                            vm.loadAgent(agent);
                            connectorAddr = vm.getAgentProperties()
                                    .getProperty("com.sun.management.jmxremote.localConnectorAddress");
                        }
                        JMXServiceURL serviceURL = new JMXServiceURL(connectorAddr);
                        connector = JMXConnectorFactory.connect(serviceURL);
                        connection = connector.getMBeanServerConnection();
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Can't connect to given Process {}", process);
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
            LOGGER.warn("Can't query object name from {} ({}) {}", process, type, objectName);
        }

        try {
            connector.close();
        } catch (Exception e) {
            LOGGER.trace("Can't close JMX connector", e);
        }

        LOGGER.debug("Karaf Decanter  JMX Local Process Collector harvesting {} done", type);
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
