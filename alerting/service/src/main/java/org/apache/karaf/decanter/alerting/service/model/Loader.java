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
package org.apache.karaf.decanter.alerting.service.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

public class Loader {

    private final static Logger LOGGER = LoggerFactory.getLogger(Loader.class);

    static public List<Rule> load(Dictionary<String, Object> configuration) {
        List<Rule> rules = new ArrayList<>();
        if (configuration == null) {
            return rules;
        }
        Enumeration<String> keys = configuration.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith("rule.")) {
                Rule rule = new Rule();
                rule.setName(key.substring("rule.".length()));
                String ruleDefinition = (String) configuration.get(key);
                ruleDefinition = ruleDefinition.replaceAll("'", "\"");
                if (ruleDefinition != null && !ruleDefinition.isEmpty()) {
                    JsonReader jsonReader = Json.createReader(new StringReader(ruleDefinition));
                    JsonObject jsonObject = jsonReader.readObject();
                    if (jsonObject.isNull("condition")) {
                        LOGGER.error("Can't load rule {} as condition is null", ruleDefinition);
                    } else {
                        rule.setCondition(jsonObject.getString("condition"));
                        if (jsonObject.get("period") == null) {
                            rule.setPeriod(null);
                        } else {
                            rule.setPeriod(jsonObject.getString("period"));
                        }
                        if (jsonObject.get("level") == null) {
                            rule.setLevel("WARN");
                        } else {
                            rule.setLevel(jsonObject.getString("level"));
                        }
                        if (jsonObject.get("recoverable") == null) {
                            rule.setRecoverable(false);
                        } else {
                            rule.setRecoverable(jsonObject.getBoolean("recoverable"));
                        }
                        rules.add(rule);
                    }
                }
            }
        }
        return rules;
    }

    static public Long oldestPeriod(List<Rule> rules) {
        if (rules == null || rules.size() == 0) {
            return null;
        }
        long oldest = Long.MAX_VALUE;
        for (Rule rule : rules) {
            long period = PeriodParser.parse(rule.getPeriod());
            if (period < oldest) {
                oldest = period;
            }
        }
        return oldest;
    }

}
