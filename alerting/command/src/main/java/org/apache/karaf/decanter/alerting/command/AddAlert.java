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

@Service
@Command(scope = "decanter", name = "alert-add", description = "Add an alert manually")
public class AddAlert implements Action {

    @Reference
    private AlertStore store;

    @Argument(index = 0, name = "level", description = "Alert level", required = true, multiValued = false)
    @Completion(LevelCompleter.class)
    String level;

    @Argument(index = 1, name = "name", description = "Alert name", required = true, multiValued = false)
    String name;

    @Override
    public Object execute() throws Exception {
        store.add(name, AlertStore.Level.valueOf(level));
        return null;
    }

}
