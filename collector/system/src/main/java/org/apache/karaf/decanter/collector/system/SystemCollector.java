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

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private String topic;
    private int threadNumber;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        this.properties = context.getProperties();
        this.topic = context.getProperties().get(EventConstants.EVENT_TOPIC) != null ? String.class.cast(context.getProperties().get(EventConstants.EVENT_TOPIC)) : "decanter/collect/system/";
        if (!this.topic.endsWith("/")) {
            this.topic = this.topic + "/";
        }
        try {
            this.threadNumber = context.getProperties().get("thread.number") != null ? Integer.parseInt(String.class.cast(context.getProperties().get("thread.number"))) : 1;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid parameter 'thread.number' is not a number");
        }
    }

    @Override
    public void run() {
        if (properties != null) {
            final String karafName = System.getProperty("karaf.name");
            final String topic = this.topic;
            String hostAddress = null;
            String hostName = null;
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                // nothing to do
            }

            Collection<Callable<Object>> callables = new ArrayList<>();

            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                if (key.startsWith("command.")) {
                    String finalHostAddress = hostAddress;
                    String finalHostName = hostName;
                    callables.add(() -> {
                        Event event = null;
                        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                            String command = (String) properties.get(key);
                            LOGGER.debug("Executing {} ({})", command, key);
                            CommandLine cmdLine = CommandLine.parse(command);
                            DefaultExecutor executor = new DefaultExecutor();
                            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
                            executor.setStreamHandler(streamHandler);
                            HashMap<String, Object> data = new HashMap<>();
                            data.put("timestamp", System.currentTimeMillis());
                            data.put("type", "system");
                            data.put("karafName", karafName);
                            data.put("hostAddress", finalHostAddress);
                            data.put("hostName", finalHostName);
                            executor.execute(cmdLine);
                            outputStream.flush();
                            String output = outputStream.toString();
                            if (output.endsWith("\n")) {
                                output = output.substring(0, output.length() - 1);
                            }
                            output = output.trim();
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
                                data.put(key, output);
                            }
                            streamHandler.stop();
                            event = new Event(topic + key.replace(".", "_"), data);
                        } catch (Exception e) {
                            LOGGER.warn("Command {} execution failed", key, e);
                        }
                        return event;
                    });
                }
            }

            ExecutorService executorService = Executors.newFixedThreadPool(this.threadNumber);
            try {
                LOGGER.debug("Start invoking system commands...");
                List<Future<Object>> results = executorService.invokeAll(callables);
                results.stream().forEach(objectFuture -> {
                    try {
                        Event event = Event.class.cast(objectFuture.get());
                        if (Optional.ofNullable(event).isPresent()) {
                            dispatcher.postEvent(event);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.warn("Thread executor for the collector system failed", e);
                    }
                });
            } catch (InterruptedException e) {
                LOGGER.warn("Thread executor for the collector system failed", e);
            }
            executorService.shutdown();
            LOGGER.debug("Invoking system commands done");
        }
    }

}
