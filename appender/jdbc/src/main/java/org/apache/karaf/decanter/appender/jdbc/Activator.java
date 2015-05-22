package org.apache.karaf.decanter.appender.jdbc;

import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

public class Activator implements BundleActivator, ManagedService {

	private static Logger logger = LoggerFactory.getLogger(Activator.class);
	private JdbcAppender appender;
	private static final String CONFIG_PID = "org.apache.karaf.decanter.appender.jdbc";
	private ServiceTracker<DataSource, ServiceRegistration> tracker;
	private BundleContext bundleContext;
	private ServiceRegistration serviceRegistration;

	@Override
	public void start(final BundleContext bundleContext) throws Exception {
		this.bundleContext = bundleContext;

		Dictionary<String, String> properties = new Hashtable<>();
		properties.put(Constants.SERVICE_PID, CONFIG_PID);
		serviceRegistration = bundleContext.registerService(ManagedService.class.getName(), this, properties);

		updated(null);
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		if (appender != null)
			appender.close();
		if (tracker != null)
			tracker.close();
		if (serviceRegistration != null)
			serviceRegistration.unregister();
	}

	@Override
	public void updated(final Dictionary config) throws ConfigurationException {

		try {
			tracker = new ServiceTracker<DataSource, ServiceRegistration>(bundleContext,
					bundleContext.createFilter("(name=jdbc-appender)"), null) {
				@Override
				public ServiceRegistration<?> addingService(ServiceReference reference) {
					Properties properties = new Properties();

					boolean UsePoolPreparedStatement = config != null ? (boolean) config
							.get("UsePoolPreparedStatement") : true;
					int maxPreparedStatements = config != null ? Integer.parseInt((String) config
							.get("maxPreparedStatements")) : 50;
					String tableName = config != null ? (String) config.get("tableName") : "Decanter";
					String columnName1 = config != null ? (String) config.get("columnName1") : "event_name";
					String columnName2 = config != null ? (String) config.get("columnName2") : "event_content";
					DataSource dataSource = (DataSource) bundleContext.getService(reference);

					appender = new JdbcAppender(dataSource, UsePoolPreparedStatement, maxPreparedStatements, tableName,
							columnName1, columnName2);

					properties.put(EventConstants.EVENT_TOPIC, "decanter/*");

					return bundleContext.registerService(EventHandler.class, appender, (Dictionary) properties);
				}

				@Override
				public void removedService(ServiceReference<DataSource> reference, ServiceRegistration service) {
					service.unregister();
					super.removedService(reference, service);
				}
			};
		} catch (InvalidSyntaxException e) {
			logger.error(e.getMessage());
		}

		tracker.open();
	}

}
