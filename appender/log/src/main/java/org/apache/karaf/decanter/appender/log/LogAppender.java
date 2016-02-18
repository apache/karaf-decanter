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
package org.apache.karaf.decanter.appender.log;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter Log Appender
 * Listens on EventAdmin and writes to the slf4j logger.
 * Be careful when combining with the log collector as it easily creates a loop.
 */
@Component(
    name = "org.apache.karaf.decanter.appender.log",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class LogAppender implements EventHandler {

    private final Logger LOGGER = LoggerFactory.getLogger(LogAppender.class);

    @Override
    public void handleEvent(Event event) {
        StringBuilder builder = new StringBuilder();
        for (String innerKey : event.getPropertyNames()) {
            builder.append(innerKey).append(":").append(event.getProperty(innerKey).toString()).append(" | ");
        }
        LOGGER.info(builder.toString());
    }

}
