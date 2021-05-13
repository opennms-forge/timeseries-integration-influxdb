package org.opennms.timeseries.impl.influxdb;

import org.junit.Ignore;

@Ignore
public class NonblockingInfluxdbStorageIntegrationTest extends AbstractInfluxdbStorageIntegrationTest {

    @Override
    protected InfluxdbConfig.WriteStrategy getWriteStrategy() {
        return InfluxdbConfig.WriteStrategy.nonblocking;
    }
}
