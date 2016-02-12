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
package org.apache.karaf.decanter.appender.elasticsearch;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WorkFinishedListener implements BulkProcessor.Listener {
    final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);
    private final AtomicLong pendingBulkItemCount = new AtomicLong();
    private int concurrentRequests;

    /**
     * @param elasticsearchAppender
     */
    public WorkFinishedListener(int concurrentRequests) {
        this.concurrentRequests = concurrentRequests;
    }

    
    @Override
    public void beforeBulk(long executionId, BulkRequest request) {
        pendingBulkItemCount.addAndGet(request.numberOfActions());
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
        ElasticsearchAppender.LOGGER.warn("Can't append into Elasticsearch", failure);
        pendingBulkItemCount.addAndGet(-request.numberOfActions());
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
        pendingBulkItemCount.addAndGet(-response.getItems().length);
    }
    
    public void waitFinished() {
        while(concurrentRequests > 0 && pendingBulkItemCount.get() > 0) {
            LockSupport.parkNanos(1000*50);
        }
    }
}