package org.apache.karaf.decanter.collector.log;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class LogAppenderTest {

    @Test
    public void testCleanLoggerName() {
        LogAppender appender = new LogAppender();
        
        String loggerName = "wrong$Pattern%For&event!Name";
        String cleanedLoggerName = appender.cleanLoggerName(loggerName);

        assertThat(cleanedLoggerName, not(containsString("%")));
        assertThat(cleanedLoggerName, not(containsString("$")));
        assertThat(cleanedLoggerName, not(containsString("&")));
        assertThat(cleanedLoggerName, not(containsString("!")));
        assertThat(cleanedLoggerName, containsString("_"));
        
    }

}
