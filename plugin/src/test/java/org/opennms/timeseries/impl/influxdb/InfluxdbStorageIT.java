package org.opennms.timeseries.impl.influxdb;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.opennms.integration.api.v1.timeseries.Aggregation;
import org.opennms.integration.api.v1.timeseries.Metric;
import org.opennms.integration.api.v1.timeseries.Sample;
import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.integration.api.v1.timeseries.TimeSeriesFetchRequest;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableSample;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTag;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableTimeSeriesFetchRequest;
import org.opennms.timeseries.impl.influxdb.shell.InitInfluxdb;

public class InfluxdbStorageIT {

    private InfluxdbStorage storage;
    private Process influxdb;

    @Before
    public void setUp() throws IOException, InterruptedException {
//        influxdb = new ProcessBuilder().command("docker run -p 9999:9999 quay.io/influxdb/influxdb:2.0.0-beta --reporting-disabled").start();
//        Thread.sleep(10000); // wait for docker to start
        String accessToken = new InitInfluxdb().setupInflux();
        storage = new InfluxdbStorage(accessToken);
    }

    @After
    public void tearDown() {
        if(influxdb!= null && influxdb.isAlive()) {
            influxdb.destroy();
        }
    }

    /**
     * Needs a running instance of Influxdb.
     *
     */
    // @Test
    public void shouldStoreAndRetrieve() throws StorageException, InterruptedException {
        Metric metric = ImmutableMetric.builder()
                .intrinsicTag("name", "NewtsRingBufMaxSize")
                .intrinsicTag("resourceId", "snmp:1:opennms-jvm:org_opennms_newts_name_ring_buffer_max_size_unit=unknown")
                .metaTag("_idx0", "(snmp,4)")
                .metaTag("_idx1", "(snmp:1,4)")
                .metaTag("_idx2", "(snmp:1:opennms-jvm,4)")
                .metaTag("_idx3", "(snmp:1:opennms-jvm:OpenNMS_Name_Notifd,4)")

                .build();
        Sample sample = ImmutableSample.builder()
                .time(Instant.now().with(ChronoField.MICRO_OF_SECOND, 0)) // Influxdb doesn't have microseconds
                .value(42.3)
                .metric(metric)
                .build();

        storage.store(Collections.singletonList(sample));
        Thread.sleep(5000); // wait for a bit to make sure data was saved.
        List<Metric> metricsRetrieved = storage.getMetrics(Collections.singletonList(new ImmutableTag("_idx1", "(snmp:1,4)")));
        assertEquals(1, metricsRetrieved.size());
        assertEquals(metric, metricsRetrieved.get(0));
        TimeSeriesFetchRequest request = ImmutableTimeSeriesFetchRequest.builder()
                .start(Instant.now().minusSeconds(300))
                .end(Instant.now())
                .metric(metric)
                .aggregation(Aggregation.NONE)
                .step(Duration.ZERO)
                .build();
        List<Sample> samplesFromDB = storage.getTimeseries(request);
        assertEquals(1, samplesFromDB.size());
        assertEquals(sample, samplesFromDB.get(0));
    }
}
