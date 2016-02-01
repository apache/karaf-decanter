package org.apache.karaf.decanter.appender.elasticsearch;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class workFinishedListener implements BulkProcessor.Listener {
    final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);
    private final AtomicLong pendingBulkItemCount = new AtomicLong();
    private int concurrentRequests;

    /**
     * @param elasticsearchAppender
     */
    public workFinishedListener(int concurrentRequests) {
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