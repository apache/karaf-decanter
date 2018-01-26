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
package org.apache.karaf.decanter.collector.file;

import java.io.File;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    name="org.apache.karaf.decanter.collector.file",
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true
)
public class DecanterTailerListener extends TailerListenerAdapter {

    @Reference
    public EventAdmin dispatcher;

    private final static Logger LOGGER = LoggerFactory.getLogger(DecanterTailerListener.class);

    private String type;
    private String path;
    
    /**
     * additional properties provided by the user
     */
    private Dictionary<String, Object> properties;
    private Tailer tailer;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws ConfigurationException {
        properties = context.getProperties();
        if (properties.get("type") == null) {
            throw new ConfigurationException("type", "type property is mandatory");
        }
        String type = (String) properties.get("type");
        if (properties.get("path") == null) {
            throw new ConfigurationException("path", "path property is mandatory");
        }
        String path = (String) properties.get("path");

        LOGGER.debug("Starting tail on {}", path);
        tailer = new Tailer(new File(path), this);
        Thread thread = new Thread(tailer, "Log Tailer for " + path);
        thread.start();
        this.type = type;
        this.path = path;
    }
    
    @Deactivate
    public void deactivate() {
        tailer.stop();
    }

    @Override
    public void handle(String line) {
        LOGGER.trace("Handle new line in {}", this.path);
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("path", path);
        String karafName = System.getProperty("karaf.name");
        if (karafName != null) {
            data.put("karafName", karafName);
        }

        try {
            data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
            data.put("hostName", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            LOGGER.debug("Can't get host address and name", e);
        }

        // custom fields
        addPropertiesTo(data);

        // TODO: try some line parsing
        data.put("line", line);

        Event event = new Event("decanter/collect/file/" + type, data);
        dispatcher.postEvent(event);
    }

    private void addPropertiesTo(Map<String, Object> data) {
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            data.put(key, properties.get(key));
        }
    }

    @Override
    public void handle(Exception e) {
        super.handle(e);
        LOGGER.warn("Handle exception on file {}", path, e);
    }

    @Override
    public void fileNotFound() {
        super.fileNotFound();
        LOGGER.warn("File {} is not found", path);
    }

    @Override
    public void fileRotated() {
        super.fileRotated();
        LOGGER.debug("File {} rotated", path);
    }

}
