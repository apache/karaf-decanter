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
package org.apache.karaf.decanter.appender.socket;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Dictionary;

@Component(
    name = "org.apache.karaf.decanter.appender.socket",
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class SocketAppender implements EventHandler {

    public static final String HOST_PROPERTY = "host";
    public static final String PORT_PROPERTY = "port";
    public static final String CONNECTED_PROPERTY = "connected";

    public static final String HOST_DEFAULT = "localhost";
    public static final String PORT_DEFAULT = "34343";
    public static final String CONNECTED_DEFAULT = "false";

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(SocketAppender.class);

    private Dictionary<String, Object> config;

    private Socket socket;
    private PrintWriter writer;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) throws Exception {
        this.config = config;
        boolean connected = Boolean.parseBoolean(getValue(config, CONNECTED_PROPERTY, CONNECTED_DEFAULT));
        if (connected) {
            try {
                initConnection();
            } catch (Exception e) {
                LOGGER.error("Can't create socket", e);
                throw e;
            }
        }
    }

    @Deactivate
    public void deactivate() {
        closeConnection();
    }

    private void initConnection() throws Exception {
        socket = new Socket(
                getValue(config, HOST_PROPERTY, HOST_DEFAULT),
                Integer.parseInt(getValue(config, PORT_PROPERTY, PORT_DEFAULT)));
        writer = new PrintWriter(socket.getOutputStream(), true);
    }

    private void closeConnection() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                // nothing to do
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                // nothing to do
            }
        }
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            String data = marshaller.marshal(event);

            boolean connected = Boolean.parseBoolean(getValue(config, CONNECTED_PROPERTY, CONNECTED_DEFAULT));

            try {

                if (!connected && socket == null) {
                    initConnection();
                }

                writer.println(data);

                if (!connected) {
                    closeConnection();
                }
            } catch (Exception e) {
                LOGGER.warn("Error sending data on the socket", e);
            }
        }
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

}
