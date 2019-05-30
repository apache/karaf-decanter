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
package org.apache.karaf.decanter.appender.websocket;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;


@Component(
        name = "org.apache.karaf.decanter.appender.websocket.servlet",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
@WebSocket
public class DecanterWebSocketAppender implements EventHandler {

    public static final String ALIAS_PROPERTY = "servlet.alias";

    public static final String ALIAS_DEFAULT = "/decanter-websocket";

    private static final Logger LOG = LoggerFactory.getLogger(DecanterWebSocketAppender.class);

    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());

    @Reference
    private Marshaller marshaller;

    @Reference
    private HttpService httpService;

    private Dictionary<String, Object> config;

    @OnWebSocketConnect
    public void onOpen(Session session) {
        session.setIdleTimeout(-1);
        sessions.add(session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
    }

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        this.config = componentContext.getProperties();
        String alias = (String) config.get(ALIAS_PROPERTY);
        if (alias == null) {
            alias = ALIAS_DEFAULT;
        }
        httpService.registerServlet(alias, new DecanterWebSocketServlet(), null, null);
    }

    @Deactivate
    public void deactivate() throws Exception {
        String alias = (String) config.get(ALIAS_PROPERTY);
        if (alias == null) {
            alias = ALIAS_DEFAULT;
        }
        httpService.unregister(alias);
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            String message = marshaller.marshal(event);
            synchronized (sessions) {
                for (Session session : sessions) {
                    try {
                        session.getRemote().sendString(message);
                    } catch (Exception e) {
                        LOG.warn("Can't publish to remote websocket endpoint", e);
                    }
                }
            }
        }
    }

}
