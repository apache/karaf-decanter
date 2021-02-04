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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;

public class CamelEventMapper {
    
    public Map<String, Object> toMap(CamelEvent event) throws UnknownHostException {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("eventType", event.getClass().getName());
        data.put("type", "camelEvent");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());
        data.put("timestamp", System.currentTimeMillis());
        
        Object source = event.getSource();
        if (event instanceof CamelEvent.ExchangeSentEvent) {
            CamelEvent.ExchangeSentEvent sent = (CamelEvent.ExchangeSentEvent) event;
            data.put("sentToEndpointUri", sent.getEndpoint()
                    .getEndpointUri());
            data.put("sentTimeTaken", sent.getTimeTaken());
        }
        if (event instanceof CamelEvent.ExchangeSendingEvent) {
            CamelEvent.ExchangeSendingEvent sending = (CamelEvent.ExchangeSendingEvent) event;
            data.put("sendingToEndpointUri", sending.getEndpoint().getEndpointUri());
        }
        if (event instanceof CamelEvent.ExchangeFailureHandledEvent) {
            CamelEvent.ExchangeFailureHandledEvent failHandled = (CamelEvent.ExchangeFailureHandledEvent) event;
            data.put("failureIsDeadLetterChannel", failHandled.isDeadLetterChannel());
            data.put("failureHandler", failHandled.getFailureHandler() == null ? "null"
                            : failHandled.getFailureHandler().getClass().getName());
        }
        if (event instanceof CamelEvent.ExchangeRedeliveryEvent) {
            CamelEvent.ExchangeRedeliveryEvent redelivery = (CamelEvent.ExchangeRedeliveryEvent) event;
            data.put("redeliveryAttempt", redelivery.getAttempt());
        }
        if (source instanceof Route) {
            Route route = (Route)source;
            data.put("routeId", route.getId());
            data.put("camelContextName", route.getCamelContext().getName());
        }
        if (source instanceof CamelContext) {
            CamelContext context = (CamelContext)source;
            data.put("camelContextName", context.getName());
        }
            
        if (event instanceof CamelEvent.ServiceStartupFailureEvent) {
            CamelEvent.ServiceStartupFailureEvent service = (CamelEvent.ServiceStartupFailureEvent) event;
            data.put("serviceName", service.getService().getClass().getName());
            data.put("cause", service.getCause().toString());
        }
        if (event instanceof CamelEvent.ServiceStopFailureEvent) {
            CamelEvent.ServiceStopFailureEvent service = (CamelEvent.ServiceStopFailureEvent) event;
            data.put("serviceName", service.getService().getClass().getName());
            data.put("cause", service.getCause().toString());
        }
        if (event instanceof CamelEvent.CamelContextResumeFailureEvent) {
            CamelEvent.CamelContextResumeFailureEvent context = (CamelEvent.CamelContextResumeFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        if (event instanceof CamelEvent.CamelContextStartupFailureEvent) {
            CamelEvent.CamelContextStartupFailureEvent context = (CamelEvent.CamelContextStartupFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        if (event instanceof CamelEvent.CamelContextStopFailureEvent) {
            CamelEvent.CamelContextStartupFailureEvent context = (CamelEvent.CamelContextStartupFailureEvent) event;
            data.put("cause", context.getCause().toString());
        }
        return data;
    }
    
}
