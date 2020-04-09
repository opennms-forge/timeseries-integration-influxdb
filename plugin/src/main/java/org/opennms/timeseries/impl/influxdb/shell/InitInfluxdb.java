package org.opennms.timeseries.impl.influxdb.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;

@Command(scope = "opennms-influxdb", name = "init", description = "Initializes Influxdb 1.x for the Timeseries Integration Influxdb Plugin.")
@Service
public class InitInfluxdb implements Action {

    @Option(name = "-l", aliases = {"--link"}, description = "The url to InfluxDB, default: http://localhost:8086.")
    private String configUrl = "http://localhost:8086";

    @Option(name = "-d", aliases = {"--database"}, description = "Specifies which database to create, default: opennms.")
    private String database = "opennms";

    @Option(name = "-u", aliases = {"--user"}, description = "The user name for InfluxDB, default: root.")
    private String configUser = "root";

    @Option(name = "-p", aliases = {"--password"}, description = "The password for InfluxDB, default: root.")
    private String configPassword = "root";

    @Override
    public Object execute() throws Exception {
        setupInflux();
        return null;
    }

    public void setupInflux() {
        InfluxDB influxDB = InfluxDBFactory.connect(configUrl, configUser, configPassword);
        influxDB.query(new Query("CREATE DATABASE " + database));
        System.out.println(String.format("Database %s created.", this.database));
    }
}
