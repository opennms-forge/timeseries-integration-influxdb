package org.opennms.timeseries.impl.influxdb.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.domain.OnboardingRequest;
import com.influxdb.client.domain.OnboardingResponse;

import okhttp3.OkHttpClient;

@Command(scope = "opennms-influxdb", name = "init", description = "Initializes the database tables for the Timeseries Integration Timescale Plugin.")
@Service
public class InitInfluxdb implements Action {

    @Reference
    private DataSource dataSource;

    @Option(name = "-b", aliases = {"--bucket"}, description = "Specifies which bucket to create, default: opennms.")
    private String configBucket = "opennms";

    @Option(name = "-o", aliases = {"--organization"}, description = "Specifies which organization to create, default: opennms.")
    private String configOrg = "opennms";

    @Option(name = "-l", aliases = {"--link"}, description = "The url to InfluxDB, default: opennms.")
    private String configUrl = "http://localhost:9999";

    @Option(name = "-u", aliases = {"--user"}, description = "The user name for InfluxDB, default: opennms.")
    private String configUser = "opennms";

    @Option(name = "-p", aliases = {"--password"}, description = "The password for InfluxDB, default: opennms.")
    private String configPassword = "password";

    @Override
    public Object execute() throws Exception {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .bucket(configBucket)
                .connectionString(configUrl)
                .org(configOrg)
                .url(configUrl)
                .okHttpClient(builder)
                .build();
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(options);

        System.out.println("Checking preconditions");
        if(!influxDBClient.isOnboardingAllowed()) {
            System.out.println("Onboarding via api is not allowed, please set it up manually. Bye!");
        }
        System.out.println("Preconditions: ok");
        System.out.println(String.format("Create account with user=%s, organization=%s, bucket=%s on url=%s", configUser, configOrg, configBucket, configUrl));
        OnboardingRequest request = new OnboardingRequest()
                .bucket(configBucket)
                .org(configOrg)
                .username(configUser)
                .password(configPassword);
        OnboardingResponse response = influxDBClient.onBoarding(request);
        System.out.println("Create account: ok");
        System.out.println("Access token is: " + response.getAuth().getToken());
        System.out.println("Enjoy!");

        return null;
    }

    private static void loadOpenNMSProperties() throws FileNotFoundException, IOException {
        // Find the opennms.properties file
        File props = Paths.get(System.getProperty("opennms.home"), "etc", "opennms.properties").toFile();
        if (!props.canRead()) {
            throw new IOException("Cannot read opennms.properties file: " + props);
        }

        // Load the properties
        try (FileInputStream fis = new FileInputStream(props)) {
            Properties p = new Properties();
            p.load(fis);

            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                String propertyName = entry.getKey().toString();
                Object value = entry.getValue();
                // Only set the value of a system property if it is not already set
                if (System.getProperty(propertyName) == null && value != null) {
                    System.setProperty(propertyName, value.toString());
                }
            }
        }
    }
}
