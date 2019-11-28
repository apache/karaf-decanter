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
package org.apache.karaf.decanter.collector.rest.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter REST Servlet Collector
 */
@Component(
    service = Servlet.class,
    name = "org.apache.karaf.decanter.collector.rest.servlet",
    immediate = true,
    property = { "decanter.collector.name=rest-servlet", "alias=/decanter/collect" }
)
public class RestServletCollector extends HttpServlet {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Unmarshaller unmarshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(RestServletCollector.class);

    private String baseTopic;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws MalformedURLException {
        Dictionary<String, Object> props = context.getProperties();
        this.baseTopic = getProperty(props, "topic", "decanter/collect/rest-servlet");
        this.properties = props;
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String)properties.get(key) : defaultValue;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        LOGGER.debug("Karaf Decanter REST Servlet Collector request received from {}", req.getRequestURI());
        try {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            String payload = builder.toString();

            Map<String, Object> data = unmarshaller.unmarshal(new ByteArrayInputStream(payload.getBytes()));
            data.put("type", "restservlet");
            data.put("payload", payload);

            PropertiesPreparator.prepare(data, properties);

            Event event = new Event(baseTopic, data);
            dispatcher.postEvent(event);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            LOGGER.debug("Karaf Decanter REST Servlet Collector harvesting done");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            LOGGER.warn("Error processing event from servlet", e);
        }
        
    }

}
