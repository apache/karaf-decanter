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
package org.apache.karaf.decanter.collector.rest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
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
@Component //
(//
    service = Servlet.class, //
    name = "org.apache.karaf.decanter.collector.rest.servlet", //
    immediate = true, //
    property = //
    { //
      "decanter.collector.name=rest-servlet", "alias=/decanter/collect"
    } //
)
public class RestCollector extends HttpServlet {
    private static final long serialVersionUID = -2042284119223927588L;

    private final static Logger LOGGER = LoggerFactory.getLogger(RestCollector.class);

    private String baseTopic;

    private EventAdmin eventAdmin;
    private Unmarshaller unmarshaller;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws MalformedURLException {
        Dictionary<String, Object> props = context.getProperties();
        this.baseTopic = getProperty(props, "topic", "decanter/collect/rest");
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String)properties.get(key) : defaultValue;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        try {
            Map<String, Object> properties = unmarshaller.unmarshal(req.getInputStream());
            Event event = new Event(baseTopic, properties);
            eventAdmin.postEvent(event);
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            LOGGER.warn("Error processing event from servlet", e);
        }
        
    }
    
    @Reference
    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    @Reference
    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }
}
