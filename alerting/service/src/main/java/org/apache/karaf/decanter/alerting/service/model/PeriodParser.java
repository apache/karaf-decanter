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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PeriodParser {

    public static long parse(String period) throws IllegalArgumentException {
        if (period == null) {
            return 0;
        }
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(period);
        if (matcher.matches()) {
            return new Long(period);
        }
        pattern = Pattern.compile("(\\d+)MILLISECONDS");
        matcher = pattern.matcher(period);
        if (matcher.matches()) {
            return new Long(matcher.group(1));
        }
        pattern = Pattern.compile("(\\d+)SECONDS");
        matcher = pattern.matcher(period);
        if (matcher.matches()) {
            return new Long(matcher.group(1)) * 1000;
        }
        pattern = Pattern.compile("(\\d+)MINUTES");
        matcher = pattern.matcher(period);
        if (matcher.matches()) {
            return new Long(matcher.group(1)) * 60 * 1000;
        }
        pattern = Pattern.compile("(\\d+)HOURS");
        matcher = pattern.matcher(period);
        if (matcher.matches()) {
            return new Long(matcher.group(1)) * 60 * 60 * 1000;
        }
        throw new IllegalStateException("Invalid period syntax: " + period);
    }

}
