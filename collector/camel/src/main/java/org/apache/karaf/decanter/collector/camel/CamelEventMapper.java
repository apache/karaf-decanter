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
package org.apache.karaf.decanter.collector.camel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.management.event.CamelContextResumeFailureEvent;
import org.apache.camel.management.event.CamelContextStartupFailureEvent;
import org.apache.camel.management.event.CamelContextStopFailureEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.ServiceStartupFailureEvent;
import org.apache.camel.management.event.ServiceStopFailureEvent;

public class CamelEventMapper {
    
    public Map<String, Object> toMap(EventObject event) throws UnknownHostException {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("eventType", event.getClass().getName());
        data.put("type", "camelEvent");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());
        data.put("timestamp", System.currentTimeMillis());
        
        Object source = event.getSource();
        if (event instanceof ExchangeSentEvent) {
            ExchangeSentEvent sent = (ExchangeSentEvent) event;
            data.put("sentToEndpointUri", sent.getEndpoint()
                    .getEndpointUri());
            data.put("sentTimeTaken", sent.getTimeTaken());
        }
        if (event instanceof ExchangeSendingEvent) {
            ExchangeSendingEvent sending = (ExchangeSendingEvent) event;
            data.put("sendingToEndpointUri", sending.getEndpoint().getEndpointUri());
        }
        if (event instanceof ExchangeFailureHandledEvent) {
            ExchangeFailureHandledEvent failHandled = (ExchangeFailureHandledEvent) event;
            data.put("failureIsHandled", failHandled.isHandled());
            data.put("failureIsDeadLetterChannel", failHandled.isDeadLetterChannel());
            data.put("failureHandler", failHandled.getFailureHandler() == null ? "null"
                            : failHandled.getFailureHandler().getClass().getName());
        }
        if (event instanceof ExchangeRedeliveryEvent) {
            ExchangeRedeliveryEvent redelivery = (ExchangeRedeliveryEvent) event;
            data.put("redeliveryAttempt", redelivery.getAttempt());
        }
        if (source instanceof Route) {
            Route route = (Route)source;
            data.put("routeId", route.getId());
            data.put("camelContextName", route.getRouteContext().getCamelContext().getName());
        }
        if (source instanceof CamelContext) {
            CamelContext context = (CamelContext)source;
            data.put("camelContextName", context.getName());
        }
            
        if (event instanceof ServiceStartupFailureEvent) {
            ServiceStartupFailureEvent service = (ServiceStartupFailureEvent) event;
            data.put("serviceName", service.getService().getClass().getName());
            data.put("camelContextName", service.getContext().getName());
            data.put("cause", service.getCause().toString());
        }
        if (event instanceof ServiceStopFailureEvent) {
            ServiceStopFailureEvent service = (ServiceStopFailureEvent) event;
            data.put("serviceName", service.getService().getClass().getName());
            data.put("camelContextName", service.getContext().getName());
            data.put("cause", service.getCause().toString());
        }
        if (event instanceof CamelContextResumeFailureEvent) {
            CamelContextResumeFailureEvent context = (CamelContextResumeFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        if (event instanceof CamelContextStartupFailureEvent) {
            CamelContextStartupFailureEvent context = (CamelContextStartupFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        if (event instanceof CamelContextStopFailureEvent) {
            CamelContextStartupFailureEvent context = (CamelContextStartupFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        return data;
    }
    
}
