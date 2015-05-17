package org.apache.karaf.decanter.appender.jdbc;

import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

public class Activator implements BundleActivator{

    private static Logger logger = LoggerFactory.getLogger(Activator.class);
    private JdbcAppender appender;
    private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.elasticsearch";
    private ServiceTracker<DataSource, ServiceRegistration> tracker;

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        tracker = new ServiceTracker<DataSource, ServiceRegistration>(bundleContext,bundleContext.createFilter("(name=jdbc-appender)"),null){
            @Override
            public ServiceRegistration<?> addingService(ServiceReference reference) {
                Properties properties = new Properties();
                DataSource dataSource = (DataSource) bundleContext.getService(reference);
                JdbcAppender appender = new JdbcAppender(dataSource);
                return bundleContext.registerService(JdbcAppender.class , appender, (Dictionary) properties);
            }

            @Override
            public void removedService(ServiceReference<DataSource> reference, ServiceRegistration service) {
                service.unregister();
                super.removedService(reference, service);
            }
        };
        tracker.open();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        if(tracker!=null)tracker.close();
    }

}
