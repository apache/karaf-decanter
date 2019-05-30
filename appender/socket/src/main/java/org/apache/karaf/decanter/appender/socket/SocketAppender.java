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

    public static final String HOST_DEFAULT = "localhost";
    public static final String PORT_DEFAULT = "34343";

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(SocketAppender.class);

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        this.config = componentContext.getProperties();
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            Socket socket = null;
            PrintWriter writer = null;
            try {
                socket = new Socket(
                        getValue(config, HOST_PROPERTY, HOST_DEFAULT),
                        Integer.parseInt(getValue(config, PORT_PROPERTY, PORT_DEFAULT)));
                String data = marshaller.marshal(event);
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(data);
            } catch (Exception e) {
                LOGGER.warn("Error sending data on the socket", e);
            } finally {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // nothing to do
                    }
                }
            }
        }
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

}
