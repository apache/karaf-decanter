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
package org.apache.karaf.decanter.collector.jetty;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.jetty",
        service = { Handler.class },
        immediate = true
)
public class DecanterCollectorJettyHandler implements Handler {

    @Reference
    public EventAdmin dispatcher;

    private Server server;

    private boolean started = false;
    private Dictionary<String, Object> properties;

    @Activate
    public void activate(ComponentContext componentContext) {
        this.properties = componentContext.getProperties();
    }

    @Override
    public void start() throws Exception {
        started = true;
    }

    @Override
    public void stop() throws Exception {
        started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isStarting() {
        return false;
    }

    @Override
    public boolean isStopping() {
        return false;
    }

    @Override
    public boolean isStopped() {
        return !started;
    }

    @Override
    public boolean isFailed() {
        return false;
    }

    @Override
    public void addLifeCycleListener(Listener listener) {
        // nothing to do
    }

    @Override
    public void removeLifeCycleListener(Listener listener) {
        // nothing to do
    }

    @Override
    public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        Map<String, Object> data = new HashMap<>();
        data.put("request.method", httpServletRequest.getMethod());
        data.put("request.requestURI", httpServletRequest.getRequestURI());
        try {
            if (httpServletRequest.getSession() != null) {
                data.put("request.session.id", httpServletRequest.getSession().getId());
            }
        } catch (Exception e) {
            // nothing to do
        }
        data.put("request.contentType", httpServletRequest.getContentType());
        data.put("request.authType", httpServletRequest.getAuthType());
        data.put("request.contextPath", httpServletRequest.getContextPath());
        data.put("request.pathInfo", httpServletRequest.getPathInfo());
        data.put("request.pathTranslated", httpServletRequest.getPathTranslated());
        data.put("request.queryString", httpServletRequest.getQueryString());
        data.put("request.remoteUser", httpServletRequest.getRemoteUser());
        data.put("request.requestedSessionId", httpServletRequest.getRequestedSessionId());
        data.put("request.requestURL", httpServletRequest.getRequestURL());
        data.put("request.servletPath", httpServletRequest.getServletPath());
        data.put("request.localAddr", httpServletRequest.getLocalAddr());
        Enumeration<String> attributeNames = httpServletRequest.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            data.put("request.attribute." + name, httpServletRequest.getAttribute(name));
        }
        Enumeration<String> parameterNames = httpServletRequest.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            data.put("request.parameter." + name, httpServletRequest.getParameter(name));
        }
        Enumeration<String> requestHeaders = httpServletRequest.getHeaderNames();
        while (requestHeaders.hasMoreElements()) {
            String name = requestHeaders.nextElement();
            data.put("request.header." + name, httpServletRequest.getHeader(name));
        }
        data.put("response.status", httpServletResponse.getStatus());
        for (String headerName : httpServletResponse.getHeaderNames()) {
            data.put("response.header." + headerName, httpServletResponse.getHeader(headerName));
        }
        data.put("response.contentType", httpServletResponse.getContentType());
        data.put("response.characterEncoding", httpServletResponse.getCharacterEncoding());
        try {
            PropertiesPreparator.prepare(data, properties);
        } catch (Exception e) {
            // nothing to do
        }
        String topic = (properties.get(EventConstants.EVENT_TOPIC) != null) ? (String) properties.get(EventConstants.EVENT_TOPIC) : "decanter/collect/jetty";
        Event event = new Event(topic, data);
        dispatcher.postEvent(event);
    }

    @Override
    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public void destroy() {
        // nothing to do
    }

}
