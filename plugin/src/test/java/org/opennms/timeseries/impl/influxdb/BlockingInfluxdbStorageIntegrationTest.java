package org.opennms.timeseries.impl.influxdb;

public class BlockingInfluxdbStorageIntegrationTest extends AbstractInfluxdbStorageIntegrationTest {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.blocking;
    }
}
