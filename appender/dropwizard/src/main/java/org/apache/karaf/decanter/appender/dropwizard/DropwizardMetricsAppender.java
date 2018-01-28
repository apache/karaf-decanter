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
package org.apache.karaf.decanter.appender.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

@Component(
        name = "org.apache.karaf.decanter.appender.dropwizard",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class DropwizardMetricsAppender implements EventHandler {

    private final MetricRegistry registry = new MetricRegistry();

    @Override
    public void handleEvent(Event event) {
        for (String propertyName : event.getPropertyNames()) {
            final Object value = event.getProperty(propertyName);
            if (value instanceof Number) {
                registry.register(propertyName, new Gauge<Number>() {
                    @Override
                    public Number getValue() {
                        return (Number) value;
                    }
                });
            }
        }
    }


}
