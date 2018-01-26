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
package org.apache.karaf.decanter.collector.system;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;

@Component(
    name = "org.apache.karaf.decanter.collector.system",
    immediate = true,
    property = { "decanter.collector.name=system",
            "scheduler.period:Long=60",
            "scheduler.concurrent:Boolean=false",
            "scheduler.name=decanter-collector-system" }
)
public class SystemCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    private final static Logger LOGGER = LoggerFactory.getLogger(SystemCollector.class);

    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        this.properties = context.getProperties();
    }

    @Override
    public void run() {
        if (properties != null) {
            String karafName = System.getProperty("karaf.name");
            String hostAddress = null;
            String hostName = null;
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                // nothing to do
            }
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                try {
                    if (key.startsWith("command.")) {
                        HashMap<String, Object> data = new HashMap<>();
                        String command = (String) properties.get(key);
                        LOGGER.debug("Executing {} ({})", command, key);
                        CommandLine cmdLine = CommandLine.parse(command);
                        DefaultExecutor executor = new DefaultExecutor();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
                        executor.setStreamHandler(streamHandler);
                        data.put("timestamp", System.currentTimeMillis());
                        data.put("type", "system");
                        data.put("karafName", karafName);
                        data.put("hostAddress", hostAddress);
                        data.put("hostName", hostName);
                        executor.execute(cmdLine);
                        outputStream.flush();
                        String output = outputStream.toString();
                        if (output.endsWith("\n")) {
                            output = output.substring(0, output.length() - 1);
                        }
                        // try to convert to number
                        try {
                            if (output.contains(".")) {
                                Double value = Double.parseDouble(output);
                                data.put(key, value);
                            } else {
                                Integer value = Integer.parseInt(output);
                                data.put(key, value);
                            }
                        } catch (NumberFormatException e) {
                            data.put(key, outputStream.toString());
                        }
                        streamHandler.stop();
                        Event event = new Event("decanter/collect/system/" + key.replace(".", "_"), data);
                        dispatcher.postEvent(event);
                        try {
                            outputStream.close();
                        } catch (Exception e) {
                            // nothing to do
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Command {} execution failed", key, e);
                }
            }
        }
    }

}
