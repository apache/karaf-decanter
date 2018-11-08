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
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Dictionary;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component(
    name = "org.apache.karaf.decanter.appender.websocket",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
@WebSocket
public class WebSocketAppender implements EventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketAppender.class);
    private static final int CONNECT_RETRY_DELAY = 2000;

    @Reference
    public Marshaller marshaller;

    private Integer connectTimeout;
    private Integer sendTimeout;
    private Session session;
    private WebSocketClient webSocketClient;
    private URI webSocketURI;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        configure(componentContext.getProperties());
        createSession();
    }

    @Override
    public void handleEvent(Event event) {
        try {
            String data = marshaller.marshal(event);

            if(session == null || (session.isOpen() == false)) {
                createSession();
            }

            if(session != null) {
                session.getRemote().sendStringByFuture(data).get(sendTimeout, TimeUnit.MILLISECONDS);
            }

        } catch (Throwable t) {
            LOGGER.error("error sending data to websocket", t);
        }
    }

    @Deactivate
    public void deactivate(ComponentContext componentContext) throws Exception {
        try {
            if(webSocketClient != null && webSocketClient.isStarted()) {
                webSocketClient.stop();
            }
        } catch (Exception e) {
            LOGGER.error("error shutting down websocket appender", e);
        }
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    private void configure(Dictionary<String, Object> config) throws Exception {
        String uri = getValue(config, "uri", "ws://localhost:80/");
        sendTimeout =  new Integer(getValue( config,"sendTimeout", "2000"));
        connectTimeout =  new Integer(getValue( config,"sendTimeout", "2000"));
        webSocketURI =  URI.create(uri);
        webSocketClient = new WebSocketClient();
        webSocketClient.start();
    }

    private void createSession() throws Exception {
        try {
            Future<Session> sessionFuture = webSocketClient.connect(this, webSocketURI, new ClientUpgradeRequest());
            session = sessionFuture.get(connectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            //Simple retry logic, could be modified for exponential back-off
            Thread.sleep(CONNECT_RETRY_DELAY);
            throw e;
        }
    }
    
}
