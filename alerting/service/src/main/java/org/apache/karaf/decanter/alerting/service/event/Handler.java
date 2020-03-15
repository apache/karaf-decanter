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
package org.apache.karaf.decanter.alerting.service.event;

import org.apache.karaf.decanter.alerting.service.Alert;
import org.apache.karaf.decanter.alerting.service.Store;
import org.apache.karaf.decanter.alerting.service.model.Loader;
import org.apache.karaf.decanter.alerting.service.model.PeriodParser;
import org.apache.karaf.decanter.alerting.service.model.Rule;
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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;

@Component(
        name = "org.apache.karaf.decanter.alerting.service",
        immediate = true,
        property= EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class Handler implements EventHandler {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Store store;

    private final static Logger LOGGER = LoggerFactory.getLogger(Handler.class);

    private List<Rule> rules;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) {
        rules = Loader.load(configuration);
    }

    private Event prepareEvent(Alert alert, Rule rule, boolean recover) {
        HashMap<String, Object> data = new HashMap<>();
        data.putAll(alert.get());
        data.put("alertLevel", rule.getLevel());
        data.put("alertPattern", rule.getCondition());
        data.put("alertBackToNormal", recover);
        Event event = new Event("decanter/alert/" + rule.getLevel(), data);
        return event;
    }

    @Override
    public void handleEvent(Event event) {
        try {
            String uuid = store.store(event);
            for (Rule rule : rules) {
                if (rule.getPeriod() != null) {
                    long timestamp = System.currentTimeMillis() - PeriodParser.parse(rule.getPeriod());
                    List<Alert> known = store.query("(" + rule.getCondition() + ") AND NOT alertUUID:" + uuid);
                    if (known.size() == 1) {
                        List<Alert> recover = store.query("alertUUID:" + uuid + " AND NOT (" + rule.getCondition() + ")");
                        if (recover.size() != 1 && (((Long) known.get(0).get("alertTimestamp")) < timestamp)) {
                            // not recover during the period
                            dispatcher.postEvent(prepareEvent(known.get(0), rule, false));
                            store.delete(rule.getCondition());
                        }
                        store.delete("alertUUID:" + uuid);
                    } else {
                        // flag
                        List<Alert> toFlag = store.query("alertUUID:" + uuid + " AND (" + rule.getCondition() + ")");
                        if (toFlag.size() == 1) {
                            store.flag("alertUUID:" + uuid, rule.getName());
                        }
                    }
                } else {
                    if (!rule.isRecoverable()) {
                        List<Alert> alerts = store.query(rule.getCondition());
                        for (Alert alert : alerts) {
                            dispatcher.postEvent(prepareEvent(alert, rule, false));
                            store.delete("alertUUID:" + alert.get("alertUUID"));
                        }
                    } else {
                        List<Alert> known = store.query("(" + rule.getCondition() + ") AND NOT alertUUID:" + uuid);
                        if (known.size() == 1) {
                            List<Alert> recover = store.query("alertUUID:" + uuid + " AND NOT (" + rule.getCondition() + ")");
                            if (recover.size() == 1) {
                                dispatcher.postEvent(prepareEvent(recover.get(0), rule, true));
                                store.delete(rule.getCondition());
                            }
                            store.delete("alertUUID:" + uuid);
                        } else {
                            List<Alert> toSend = store.query("alertUUID:" + uuid + " AND (" + rule.getCondition() + ")");
                            if (toSend.size() == 1) {
                                store.flag("alertUUID:" + uuid, rule.getName());
                                dispatcher.postEvent(prepareEvent(toSend.get(0), rule, false));
                            }
                        }
                    }
                }
            }
            store.eviction();
        } catch (Exception e) {
            LOGGER.error("Can't process alerting for event", e);
        }
    }

    /**
     * Visible for testing only.
     */
    void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Visible for testing only.
     */
    void setStore(Store store) {
        this.store = store;
    }

}
