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

import org.apache.karaf.decanter.api.Appender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Karaf Decanter Log Appender, logging the collected data using.
 */
public class LogAppender implements Appender {

    private final Logger LOGGER = LoggerFactory.getLogger(LogAppender.class);

    public void append(Map<Long, Map<String, Object>> data) {
        for (Long key : data.keySet()) {
            Map<String, Object> inner = data.get(key);
            StringBuilder builder = new StringBuilder();
            builder.append(key).append(" - ");
            for (String innerKey : inner.keySet()) {
                builder.append(innerKey).append(":").append(inner.get(innerKey).toString()).append(" | ");
            }
            LOGGER.info(builder.toString());
        }
    }

}
