package org.apache.karaf.decanter.collector.camel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.RouteNode;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.CamelContextResumeFailureEvent;
import org.apache.camel.management.event.CamelContextStartupFailureEvent;
import org.apache.camel.management.event.CamelContextStopFailureEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.ServiceStartupFailureEvent;
import org.apache.camel.management.event.ServiceStopFailureEvent;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecanterEventNotifier extends EventNotifierSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DecanterEventNotifier.class.getName());

    private EventAdmin eventAdmin;
    private String camelContextMatcher = ".*";
    private String routeMatcher = ".*";
    private DecanterCamelEventExtender extender = null;
    private boolean includeProperties = true;
    private boolean includeHeaders = true;
    private boolean includeBody = true;

    public EventAdmin getEventAdmin() {
        return eventAdmin;
    }

    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    public void setCamelContextMatcher(String camelContextMatcher) {
        this.camelContextMatcher = camelContextMatcher;
    }

    public void setRouteMatcher(String routeMatcher) {
        this.routeMatcher = routeMatcher;
    }

    public void setExtender(DecanterCamelEventExtender extender) {
        this.extender = extender;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public void setIncludeProperties(boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    @Override
    public boolean isEnabled(EventObject eventObject) {
        if (eventObject == null) {
            return false;
        }
        Object source = eventObject.getSource();
        if (source instanceof Exchange) {
            Exchange exchange = (Exchange)source;
            boolean contextMatches = exchange.getContext().getName().matches(camelContextMatcher);
            if (exchange.getFromRouteId() != null) {
                return exchange.getFromRouteId().matches(routeMatcher) && contextMatches;
            } else {
                return contextMatches;
            }
        } else if (source instanceof CamelContext) {
            CamelContext context = (CamelContext)eventObject.getSource();
            return context.getName().matches(camelContextMatcher);
        } else if (source instanceof Route) {
            Route route = (Route)source;
            boolean contextMatches = route.getRouteContext().getCamelContext().getName().matches(camelContextMatcher);
            return contextMatches && route.getId().matches(routeMatcher);
        } else {
            return false;
        }
    }

    public void notify(EventObject event) throws Exception {
        try {
            Map<String, Object> eventMap = createEvent(event,
                    event instanceof AbstractExchangeEvent ? ((AbstractExchangeEvent) event)
                            .getExchange() : null);
            boolean post = false;
            Object source = event.getSource();
            if (event instanceof ExchangeSentEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeSentEvents()) {
                ExchangeSentEvent sent = (ExchangeSentEvent) event;
                eventMap.put("sentToEndpointUri", sent.getEndpoint()
                        .getEndpointUri());
                eventMap.put("sentTimeTaken", sent.getTimeTaken());
                post = true;
            }
            if (event instanceof ExchangeSendingEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeSendingEvents()) {
                ExchangeSendingEvent sending = (ExchangeSendingEvent) event;
                eventMap.put("sendingToEndpointUri", sending.getEndpoint().getEndpointUri());
                post = true;
            }
            if (event instanceof ExchangeFailureHandledEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeFailedEvents()) {
                ExchangeFailureHandledEvent failHandled = (ExchangeFailureHandledEvent) event;
                eventMap.put("failureIsHandled", failHandled.isHandled());
                eventMap.put("failureIsDeadLetterChannel", failHandled.isDeadLetterChannel());
                eventMap.put("failureHandler", failHandled.getFailureHandler() == null ? "null"
                                : failHandled.getFailureHandler().getClass().getName());
                post = true;
            }
            if (event instanceof ExchangeRedeliveryEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeRedeliveryEvents()) {
                ExchangeRedeliveryEvent redelivery = (ExchangeRedeliveryEvent) event;
                eventMap.put("redeliveryAttempt", redelivery.getAttempt());
                post = true;
            }
            if (source instanceof Route && !isIgnoreRouteEvents()) {
                Route route = (Route)source;
                eventMap.put("routeId", route.getId());
                eventMap.put("camelContextName", route.getRouteContext().getCamelContext().getName());
                post = true;
            }
            if (source instanceof CamelContext && !isIgnoreCamelContextEvents()) {
                CamelContext context = (CamelContext)source;
                eventMap.put("camelContextName", context.getName());
                post = true;
            }
                
            if (event instanceof ServiceStartupFailureEvent && !isIgnoreServiceEvents()) {
                ServiceStartupFailureEvent service = (ServiceStartupFailureEvent) event;
                eventMap.put("serviceName", service.getService().getClass().getName());
                eventMap.put("camelContextName", service.getContext().getName());
                eventMap.put("cause", service.getCause().toString());
                post = true;
            }
            if (event instanceof ServiceStopFailureEvent && !isIgnoreServiceEvents()) {
                ServiceStopFailureEvent service = (ServiceStopFailureEvent) event;
                eventMap.put("serviceName", service.getService().getClass().getName());
                eventMap.put("camelContextName", service.getContext().getName());
                eventMap.put("cause", service.getCause().toString());
                post = true;
            }
            if (event instanceof CamelContextResumeFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextResumeFailureEvent context = (CamelContextResumeFailureEvent) event;
                eventMap.put("cause", context.getCause().toString());
            }
            if (event instanceof CamelContextStartupFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStartupFailureEvent context = (CamelContextStartupFailureEvent) event;
                eventMap.put("cause", context.getCause().toString());
            }
            if (event instanceof CamelContextStopFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStartupFailureEvent context = (CamelContextStartupFailureEvent) event;
                eventMap.put("cause", context.getCause().toString());
            }
            if (event instanceof ExchangeCompletedEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeCompletedEvent()) {
                post = true;
            }
            if (event instanceof ExchangeCreatedEvent && !isIgnoreExchangeEvents() && !isIgnoreExchangeCreatedEvent()) {
                post = true;
            }
            if (post) {
                eventAdmin.postEvent(new Event("decanter/collect/camel/event", eventMap));
            }
        } catch (Exception ex) {
            LOG.warn("Failed to handle event", ex);
        }
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }

    private Map<String, Object> createEvent(EventObject event, Exchange exchange) throws UnknownHostException {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("eventType", event.getClass().getName());
        data.put("type", "camelEvent");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());
        data.put("timestamp", System.currentTimeMillis());

        if (exchange == null) {
            return data;
        }
        data.put("fromEndpointUri", exchange.getFromEndpoint() != null ? exchange.getFromEndpoint().getEndpointUri() : null);
        data.put("previousNode", extractFromNode(exchange));
        data.put("toNode", extractToNode(exchange));
        data.put("exchangeId", exchange.getExchangeId());
        data.put("routeId", exchange.getFromRouteId());
        data.put("camelContextName", exchange.getContext().getName());
        data.put("shortExchangeId", extractShortExchangeId(exchange));
        data.put("exchangePattern", exchange.getPattern().toString());
        if (includeProperties) {
            data.put("properties", exchange.getProperties());
        }
        if (includeHeaders) {
            data.put("inHeaders", exchange.getIn().getHeaders());
        }
        if (includeBody) {
            data.put("inBody", MessageHelper.extractBodyAsString(exchange.getIn()));
        }
        data.put("inBodyType", MessageHelper.getBodyTypeName(exchange.getIn()));
        if (exchange.hasOut()) {
            if (includeHeaders) {
                data.put("outHeaders", exchange.getOut().getHeaders());
            }
            if (includeBody) {
                data.put("outBody", MessageHelper.extractBodyAsString(exchange.getOut()));
            }
            data.put("outBodyType", MessageHelper.getBodyTypeName(exchange.getOut()));
        }
        data.put("causedByException", extractCausedByException(exchange));
        if (extender != null) {
            extender.extend(data, exchange);
        }
        return data;
    }

    private static String extractShortExchangeId(Exchange exchange) {
        return exchange.getExchangeId().substring(exchange.getExchangeId().indexOf("/") + 1);
    }

    private static String extractFromNode(Exchange exchange) {
        if (exchange.getUnitOfWork() == null) {
            return null;
        }
        TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
        RouteNode last = traced.getSecondLastNode();
        return last != null ? last.getLabel(exchange) : null;
    }

    private static String extractToNode(Exchange exchange) {
        if (exchange.getUnitOfWork() == null) {
            return null;
        }
        TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
        RouteNode last = traced.getLastNode();
        return last != null ? last.getLabel(exchange) : null;
    }

    private static String extractCausedByException(Exchange exchange) {
        Throwable cause = exchange.getException();
        if (cause == null) {
            cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        return (cause != null) ? cause.toString() : null;
    }

}
