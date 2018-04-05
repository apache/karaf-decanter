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
package org.apache.karaf.decanter.collector.httpfilter;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.httpfilter",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = { "pattern=/*" },
        service = Filter.class,
        immediate = true
)
public class HttpFilterCollector implements Filter {

    @Reference
    public EventAdmin dispatcher;

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpFilterCollector.class);

    private Dictionary<String, Object> properties;
    private String eventAdminTopic;

    @Reference
    public Unmarshaller unmarshaller;

    @Activate
    public void register(ComponentContext context) throws IOException {
        this.properties = context.getProperties();
        eventAdminTopic = getProperty(this.properties, EventConstants.EVENT_TOPIC, "decanter/collect/httpfilter");
    }

    @Deactivate
    public void unregister() throws IOException {
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String)properties.get(key) : defaultValue;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOGGER.info("HttpFilterCollector :: init");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        LOGGER.info("HttpFilterCollector :: filtering");
        Map<String, Object> data = new HashMap<>();
        data.put("request.method", httpRequest.getMethod());
        data.put("request.uri", httpRequest.getRequestURI());
        data.put("request.session-id", httpRequest.getSession().getId());
        Enumeration<String> headers = httpRequest.getHeaderNames();
        while (headers.hasMoreElements()) {
            String name = headers.nextElement();
            data.put("request.header." + name, httpRequest.getHeader(name));
        }
        Event event = new Event(this.eventAdminTopic, data);
        this.dispatcher.postEvent(event);
    }

    @Override
    public void destroy() {

    }
}