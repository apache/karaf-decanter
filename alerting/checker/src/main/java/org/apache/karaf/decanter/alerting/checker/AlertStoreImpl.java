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
package org.apache.karaf.decanter.alerting.checker;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.HashSet;
import java.util.Set;

@Component(
        immediate = true
)
public class AlertStoreImpl implements AlertStore {

    private Set<String> errorAlerts;
    private Set<String> warnAlerts;

    @Activate
    public void activate() {
        this.errorAlerts = new HashSet<>();
        this.warnAlerts = new HashSet<>();
    }

    public void add(String name, String level) {
        if (level.equals("error")) {
            this.errorAlerts.add(name);
        }
        if (level.equals("warn")) {
            this.warnAlerts.add(name);
        }
    }

    public void remove(String name, String level) {
        if (level.equals("error")) {
            this.errorAlerts.remove(name);
        }
        if (level.equals("warn")) {
            this.warnAlerts.remove(name);
        }
    }

    public boolean known(String name, String level) {
        if (level.equals("error")) {
            return this.errorAlerts.contains(name);
        }
        if (level.equals("warn")) {
            return this.warnAlerts.contains(name);
        }
        return false;
    }

}
