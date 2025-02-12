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
package org.apache.karaf.decanter.appender.kafka;

import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;

import java.util.function.Supplier;

/**
 * Copied from Apache Kafka (org.apache.kafka.common.utils.SystemTime)
 */
public class TestSystemTime implements Time {
    private static final TestSystemTime SYSTEM_TIME = new TestSystemTime();

    public static TestSystemTime getSystemTime() {
        return SYSTEM_TIME;
    }

    public long milliseconds() {
        return System.currentTimeMillis();
    }

    public long nanoseconds() {
        return System.nanoTime();
    }

    public void sleep(long ms) {
        Utils.sleep(ms);
    }

    public void waitObject(Object obj, Supplier<Boolean> condition, long deadlineMs) throws InterruptedException {
        synchronized(obj) {
            while(!(Boolean)condition.get()) {
                long currentTimeMs = this.milliseconds();
                if (currentTimeMs >= deadlineMs) {
                    throw new TimeoutException("Condition not satisfied before deadline");
                }

                obj.wait(deadlineMs - currentTimeMs);
            }

        }
    }

    public TestSystemTime() {
    }
}
