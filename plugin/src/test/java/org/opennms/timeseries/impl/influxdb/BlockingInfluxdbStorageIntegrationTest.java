package org.opennms.timeseries.impl.influxdb;

import org.junit.Ignore;

@Ignore
public class BlockingInfluxdbStorageIntegrationTest extends AbstractInfluxdbStorageIntegrationTest {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.blocking;
    }
}
