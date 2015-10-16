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

import org.apache.commons.io.input.TailerListenerAdapter;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class DecanterTailerListener extends TailerListenerAdapter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DecanterTailerListener.class);

    private String type;
    private String path;
    private EventAdmin eventAdmin;
    private Dictionary properties;

    public DecanterTailerListener(String type, String path, EventAdmin eventAdmin, Dictionary properties) {
        this.type = type;
        this.path = path;
        this.eventAdmin = eventAdmin;
        this.properties = properties;
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

        // add additional properties (can be provided by the user)
        if (properties != null) {
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String property = keys.nextElement();
                data.put(property, properties.get(property));
            }
        }

        // TODO: try some line parsing
        data.put("line", line);

        Event event = new Event("decanter/collect/file/" + type, data);
        eventAdmin.postEvent(event);
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
