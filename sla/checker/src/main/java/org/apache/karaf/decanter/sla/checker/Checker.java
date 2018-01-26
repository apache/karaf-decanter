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
package org.apache.karaf.decanter.sla.checker;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receive all collect events to validate SLA and eventually throw alert events
 */
@Component(
    name="org.apache.karaf.decanter.sla.checker",
    immediate=true,
    property=EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class Checker implements EventHandler {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public AlertStore alertStore;

    private final static Logger LOGGER = LoggerFactory.getLogger(Checker.class);

    private Dictionary<String, Object> config;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        this.config = context.getProperties();
    }

    @Override
    public void handleEvent(Event collectEvent) {
        String type = (String) collectEvent.getProperty("type");
        for (String name : collectEvent.getPropertyNames()) {

            // error
            String errorPattern = null;
            if (config.get(name + ".error") != null) {
                errorPattern = (String) config.get(name + ".error");
            } else if (config.get(type + "." + name + ".error") != null) {
                errorPattern = (String) config.get(type + "." + name + ".error");
            }
            if (errorPattern != null) {
                Object value = collectEvent.getProperty(name);
                if (!validate(errorPattern, value)) {
                    if (!alertStore.known(name, "error")) {
                        alertStore.add(name, "error");
                        Event alertEvent = populateAlertEvent("error", collectEvent, name, errorPattern, false);
                        dispatcher.postEvent(alertEvent);
                    }
                } else {
                    if (alertStore.known(name, "error")) {
                        dispatcher.postEvent(populateAlertEvent("error", collectEvent, name, errorPattern, true));
                        alertStore.remove(name, "error");
                    }
                }
            } else {
                if (alertStore.known(name, "error")) {
                    dispatcher.postEvent(populateAlertEvent("error", collectEvent, name, "REMOVED", true));
                }
            }

            // warn
            String warnPattern = null;
            if (config.get(name + ".warn") != null) {
                warnPattern = (String) config.get(name + ".warn");
            } else if (config.get(type + "." + name + ".warn") != null) {
                warnPattern = (String) config.get(type + "." + name + ".warn");
            }
            if (warnPattern != null) {
                Object value = collectEvent.getProperty(name);
                if (!validate(warnPattern, value)) {
                    if (!alertStore.known(name, "warn")) {
                        alertStore.add(name, "warn");
                        Event alertEvent = populateAlertEvent("warn", collectEvent, name, warnPattern, false);
                        dispatcher.postEvent(alertEvent);
                    }
                } else {
                    if (alertStore.known(name, "warn")) {
                        dispatcher.postEvent(populateAlertEvent("warn", collectEvent, name, warnPattern, true));
                        alertStore.remove(name, "warn");
                    }
                }
            } else {
                if (alertStore.known(name, "warn")) {
                    dispatcher.postEvent(populateAlertEvent("warn", collectEvent, name, "REMOVED", true));
                    alertStore.remove(name, "warn");
                }
            }
        }
    }

    private Event populateAlertEvent(String level, Event collectEvent, String attribute, String pattern, boolean recovery) {
        Map<String, Object> data = new HashMap<>();
        data.put("alertLevel", level);
        data.put("alertAttribute", attribute);
        data.put("alertPattern", pattern);
        data.put("alertBackToNormal", recovery);
        for (String name : collectEvent.getPropertyNames()) {
            data.put(name, collectEvent.getProperty(name));
        }
        Event alertEvent = new Event("decanter/alert/" + level, data);
        return alertEvent;
    }

    private boolean validateNumber(String pattern, Number value) {
        LOGGER.debug("Validating Number");
        if (pattern.startsWith("range:")) {
            pattern = pattern.substring("range:".length());
            LOGGER.debug("Validating range {}", pattern);
            boolean minIncluded = false;
            boolean maxIncluded = false;
            if (pattern.startsWith("(")) {
                minIncluded = false;
            } else if (pattern.startsWith("[")) {
                minIncluded = true;
            } else {
                LOGGER.warn("The pattern {} doesn't start with ( or [", pattern);
                return true;
            }
            if (pattern.endsWith(")")) {
                maxIncluded = false;
            } else if (pattern.endsWith("]")) {
                maxIncluded = true;
            } else {
                LOGGER.warn("The pattern {} doesn't end with ) or ]", pattern);
                return true;
            }
            pattern = pattern.substring(1);
            pattern = pattern.substring(0, pattern.length() - 1);
            String[] patterns = pattern.split(",");
            if (patterns.length != 2) {
                LOGGER.warn("The range pattern {} doesn't contain ,'", pattern);
                return true;
            }
            if (patterns[0].contains(".")) {
                Float m = Float.parseFloat(patterns[0]);
                if (value instanceof BigDecimal) {
                    BigDecimal v = new BigDecimal(value.toString());
                    BigDecimal mBD = new BigDecimal(m);
                    int compare = v.compareTo(mBD);
                    if (minIncluded && compare == -1) return false;
                    else if (compare <= 0) return false;
                }
                if (value instanceof Double) {
                    double v = value.doubleValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Float) {
                    float v = value.floatValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Integer) {
                    int v = value.intValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Long) {
                    long v = value.longValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Short) {
                    short v = value.shortValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
            } else {
                int m = Integer.parseInt(patterns[0]);
                if (value instanceof BigDecimal) {
                    BigDecimal v = new BigDecimal(value.toString());
                    BigDecimal mBD = new BigDecimal(m);
                    int compare = v.compareTo(mBD);
                    if (minIncluded && compare == -1) return false;
                    else if (compare <= 0) return false;
                }
                if (value instanceof Double) {
                    double v = value.doubleValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Float) {
                    float v = value.floatValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Integer) {
                    int v = value.intValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Long) {
                    long v = value.longValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
                if (value instanceof Short) {
                    short v = value.shortValue();
                    if (minIncluded && m > v) return false;
                    else if (m >= v) return false;
                }
            }
            if (patterns[1].contains(".")) {
                Float m = Float.parseFloat(patterns[1]);
                if (value instanceof BigDecimal) {
                    BigDecimal v = new BigDecimal(value.toString());
                    BigDecimal mBD = new BigDecimal(m);
                    int compare = v.compareTo(mBD);
                    if (maxIncluded && compare <= 0) return false;
                    else if (compare < 0) return false;
                }
                if (value instanceof Double) {
                    double v = value.doubleValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Float) {
                    float v = value.floatValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Integer) {
                    int v = value.intValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Long) {
                    long v = value.longValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Short) {
                    short v = value.shortValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
            } else {
                int m = Integer.parseInt(patterns[1]);
                if (value instanceof BigDecimal) {
                    BigDecimal v = new BigDecimal(value.toString());
                    BigDecimal mBD = new BigDecimal(m);
                    int compare = v.compareTo(mBD);
                    if (maxIncluded && compare <= 0) return false;
                    else if (compare == -1) return false;
                }
                if (value instanceof Double) {
                    double v = value.doubleValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Float) {
                    float v = value.floatValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Integer) {
                    int v = value.intValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Long) {
                    long v = value.longValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m < v) return false;
                }
                if (value instanceof Short) {
                    short v = value.shortValue();
                    if (maxIncluded && m <= v) return false;
                    else if (m <= v) return false;
                }
            }
        } else if (pattern.startsWith("equal:")) {
            pattern = pattern.substring("equal:".length());
            LOGGER.debug("Validating equal {}", pattern);
            String[] patterns = pattern.split(",");
            for (String p : patterns) {
                if (p.contains(".")) {
                    float f = Float.parseFloat(p);
                    if (value instanceof BigDecimal) {
                        BigDecimal v = new BigDecimal(value.toString());
                        BigDecimal mBD = new BigDecimal(f);
                        int compare = v.compareTo(mBD);
                        if (compare != 0) return false;
                    }
                    if (value instanceof Double) {
                        double v = value.doubleValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Float) {
                        float v = value.floatValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Integer) {
                        int v = value.intValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Long) {
                        long v = value.longValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Short) {
                        short v = value.shortValue();
                        if (v != f) return false;
                    }
                } else {
                    int f = Integer.parseInt(p);
                    if (value instanceof BigDecimal) {
                        BigDecimal v = new BigDecimal(value.toString());
                        BigDecimal mBD = new BigDecimal(f);
                        int compare = v.compareTo(mBD);
                        if (compare != 0) return false;
                    }
                    if (value instanceof Double) {
                        double v = value.doubleValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Float) {
                        float v = value.floatValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Integer) {
                        int v = value.intValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Long) {
                        long v = value.longValue();
                        if (v != f) return false;
                    }
                    if (value instanceof Short) {
                        short v = value.shortValue();
                        if (v != f) return false;
                    }
                }
            }
        } else if (pattern.startsWith("notequal:")) {
            pattern = pattern.substring("notequal:".length());
            LOGGER.debug("Validating notequal {}", pattern);
            String[] patterns = pattern.split(",");
            for (String p : patterns) {
                if (p.contains(".")) {
                    float f = Float.parseFloat(p);
                    if (value instanceof BigDecimal) {
                        BigDecimal v = new BigDecimal(value.toString());
                        BigDecimal mBD = new BigDecimal(f);
                        int compare = v.compareTo(mBD);
                        if (compare == 0) return false;
                    }
                    if (value instanceof Double) {
                        double v = value.doubleValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Float) {
                        float v = value.floatValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Integer) {
                        int v = value.intValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Long) {
                        long v = value.longValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Short) {
                        short v = value.shortValue();
                        if (v == f) return false;
                    }
                } else {
                    int f = Integer.parseInt(p);
                    if (value instanceof BigDecimal) {
                        BigDecimal v = new BigDecimal(value.toString());
                        BigDecimal mBD = new BigDecimal(f);
                        int compare = v.compareTo(mBD);
                        if (compare == 0) return false;
                    }
                    if (value instanceof Double) {
                        double v = value.doubleValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Float) {
                        float v = value.floatValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Integer) {
                        int v = value.intValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Long) {
                        long v = value.longValue();
                        if (v == f) return false;
                    }
                    if (value instanceof Short) {
                        short v = value.shortValue();
                        if (v == f) return false;
                    }
                }
            }
        } else {
            LOGGER.warn("The pattern {} doesn't start with range:, equal: or notequal:", pattern);
            return true;
        }
        return true;
    }

    private boolean validateString(String pattern, String value) {
        // we use regex as we use a String
        LOGGER.debug("Validating String");
        if (pattern.startsWith("match:")) {
            pattern = pattern.substring("match:".length());
            LOGGER.debug("Validating match {}", pattern);
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(value);
            if (m.matches()) {
                return true;
            }
        } else if (pattern.startsWith("notmatch:")) {
            pattern = pattern.substring("notmatch:".length());
            LOGGER.debug("Validating notmatch {}", pattern);
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(value);
            if (!m.matches()) {
                return true;
            }
        } else {
            LOGGER.warn("The pattern {} doesn't start with match: or notmatch:", pattern);
            return true;
        }
        return false;
    }

    private boolean validate(String pattern, Object value) {
        if (value instanceof Double ||
                value instanceof Float ||
                value instanceof Integer ||
                value instanceof Long ||
                value instanceof Short ||
                value instanceof BigDecimal) {
            // we use a number checker
            return validateNumber(pattern, (Number) value);
        } else {
            // we use regex as we use a String
            return validateString(pattern, value.toString());
        }
    }
    
}
