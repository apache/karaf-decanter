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
package org.apache.karaf.decanter.alerting.command;

import org.apache.karaf.decanter.alerting.checker.AlertStore;
import org.apache.karaf.decanter.alerting.command.completers.LevelCompleter;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import java.util.Set;

@Command(scope = "decanter", name = "alerts", description = "List the current alerts")
@Service
public class ListAlerts implements Action {

    @Reference
    private AlertStore store;

    @Argument(index = 0, name = "Alert level", description = "The level of the alerts", required = true, multiValued = false)
    @Completion(LevelCompleter.class)
    String level;

    @Override
    public Object execute() throws Exception {
        AlertStore.Level alertLevel = AlertStore.Level.valueOf(level);
        Set<String> alerts = store.list(alertLevel);
        for (String alert : alerts) {
            System.out.println(alert);
        }
        return null;
    }
}
