package org.apache.karaf.decanter.collector.camel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RouteNode;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.management.event.CamelContextResumeFailureEvent;
import org.apache.camel.management.event.CamelContextResumedEvent;
import org.apache.camel.management.event.CamelContextResumingEvent;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.CamelContextStartupFailureEvent;
import org.apache.camel.management.event.CamelContextStopFailureEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.management.event.CamelContextSuspendedEvent;
import org.apache.camel.management.event.CamelContextSuspendingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteAddedEvent;
import org.apache.camel.management.event.RouteRemovedEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;
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
        if (eventObject != null) {
            if (eventObject instanceof AbstractExchangeEvent) {
                AbstractExchangeEvent event = (AbstractExchangeEvent) eventObject;
                if (event.getExchange().getFromRouteId() != null) {
                    return (event.getExchange().getFromRouteId().matches(routeMatcher) && event.getExchange().getContext().getName().matches(camelContextMatcher));
                } else {
                    return (event.getExchange().getContext().getName().matches(camelContextMatcher));
                }
            }
            if (eventObject instanceof CamelContextResumedEvent) {
                return ((CamelContextResumedEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextResumeFailureEvent) {
                return ((CamelContextResumeFailureEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextResumingEvent) {
                return ((CamelContextResumingEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStartedEvent) {
                return ((CamelContextStartedEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStartingEvent) {
                return ((CamelContextStartingEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStartupFailureEvent) {
                return ((CamelContextStartupFailureEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStopFailureEvent) {
                return ((CamelContextStopFailureEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStoppedEvent) {
                return ((CamelContextStoppedEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof CamelContextStoppingEvent) {
                return ((CamelContextStoppingEvent) eventObject).getContext().getName().matches(camelContextMatcher);
            }
            if (eventObject instanceof RouteAddedEvent) {
                return ((RouteAddedEvent) eventObject).getRoute().getRouteContext().getCamelContext().getName().matches(camelContextMatcher)
                        && ((RouteAddedEvent) eventObject).getRoute().getId().matches(routeMatcher);
            }
            if (eventObject instanceof RouteRemovedEvent) {
                return ((RouteRemovedEvent) eventObject).getRoute().getRouteContext().getCamelContext().getName().matches(camelContextMatcher)
                        && ((RouteRemovedEvent) eventObject).getRoute().getId().matches(routeMatcher);
            }
            if (eventObject instanceof RouteStartedEvent) {
                return ((RouteStartedEvent) eventObject).getRoute().getRouteContext().getCamelContext().getName().matches(camelContextMatcher)
                        && ((RouteStartedEvent) eventObject).getRoute().getId().matches(routeMatcher);
            }
            if (eventObject instanceof RouteStoppedEvent) {
                return ((RouteStoppedEvent) eventObject).getRoute().getRouteContext().getCamelContext().getName().matches(camelContextMatcher)
                        && ((RouteStoppedEvent) eventObject).getRoute().getId().matches(routeMatcher);
            }
        }
        return  false;
    }

    public void notify(EventObject event) throws Exception {
        try {
            Map<String, Object> eventMap = createEvent(event,
                    event instanceof AbstractExchangeEvent ? ((AbstractExchangeEvent) event)
                            .getExchange() : null);
            boolean post = false;
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
            if (event instanceof RouteStartedEvent && !isIgnoreRouteEvents()) {
                RouteStartedEvent route = (RouteStartedEvent) event;
                eventMap.put("routeId", route.getRoute().getId());
                eventMap.put("camelContextName", route.getRoute().getRouteContext().getCamelContext().getName());
                post = true;
            }
            if (event instanceof RouteAddedEvent && !isIgnoreRouteEvents()) {
                RouteAddedEvent route = (RouteAddedEvent) event;
                eventMap.put("routeId", route.getRoute().getId());
                eventMap.put("camelContextName", route.getRoute().getRouteContext().getCamelContext().getName());
                post = true;
            }
            if (event instanceof RouteRemovedEvent && !isIgnoreRouteEvents()) {
                RouteRemovedEvent route = (RouteRemovedEvent) event;
                eventMap.put("routeId", route.getRoute().getId());
                eventMap.put("camelContextName", route.getRoute().getRouteContext().getCamelContext().getName());
                post = true;
            }
            if (event instanceof RouteStoppedEvent && !isIgnoreRouteEvents()) {
                RouteStoppedEvent route = (RouteStoppedEvent) event;
                eventMap.put("routeId", route.getRoute().getId());
                eventMap.put("camelContextName", route.getRoute().getRouteContext().getCamelContext().getName());
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
            if (event instanceof CamelContextResumedEvent && !isIgnoreCamelContextEvents()) {
                CamelContextResumedEvent context = (CamelContextResumedEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextResumeFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextResumeFailureEvent context = (CamelContextResumeFailureEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                eventMap.put("cause", context.getCause().toString());
                post = true;
            }
            if (event instanceof CamelContextResumingEvent && !isIgnoreCamelContextEvents()) {
                CamelContextResumingEvent context = (CamelContextResumingEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextStartedEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStartedEvent context = (CamelContextStartedEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextStartingEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStartingEvent context = (CamelContextStartingEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextStartupFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStartupFailureEvent context = (CamelContextStartupFailureEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                eventMap.put("cause", context.getCause().toString());
                post = true;
            }
            if (event instanceof CamelContextStopFailureEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStopFailureEvent context = (CamelContextStopFailureEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                eventMap.put("cause", context.getCause().toString());
                post = true;
            }
            if (event instanceof CamelContextStoppedEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStoppedEvent context = (CamelContextStoppedEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextStoppingEvent && !isIgnoreCamelContextEvents()) {
                CamelContextStoppingEvent context = (CamelContextStoppingEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextSuspendedEvent && !isIgnoreCamelContextEvents()) {
                CamelContextSuspendedEvent context = (CamelContextSuspendedEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
            }
            if (event instanceof CamelContextSuspendingEvent && !isIgnoreCamelContextEvents()) {
                CamelContextSuspendingEvent context = (CamelContextSuspendingEvent) event;
                eventMap.put("camelContextName", context.getContext().getName());
                post = true;
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
            data.put("inBodyType", MessageHelper.getBodyTypeName(exchange.getIn()));
        }
        if (exchange.hasOut()) {
            if (includeHeaders) {
                data.put("outHeaders", exchange.getOut().getHeaders());
            }
            if (includeBody) {
                data.put("outBody", MessageHelper.extractBodyAsString(exchange.getOut()));
                data.put("outBodyType", MessageHelper.getBodyTypeName(exchange.getOut()));
            }
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