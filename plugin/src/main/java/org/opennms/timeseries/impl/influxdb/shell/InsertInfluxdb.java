package org.opennms.timeseries.impl.influxdb.shell;

import java.time.Instant;
import java.util.Arrays;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.timeseries.IntrinsicTagNames;
import org.opennms.integration.api.v1.timeseries.StorageException;
import org.opennms.integration.api.v1.timeseries.TimeSeriesStorage;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableSample;

@Command(scope = "opennms-influxdb", name = "test-insert", description = "Insert a test sample.")
@Service
public class InsertInfluxdb implements Action {

    @Reference
    private TimeSeriesStorage timeSeriesStorage;

    @Override
    public Object execute() throws StorageException {
        final ImmutableSample sample = ImmutableSample.builder()
                .metric(ImmutableMetric.builder()
                        .intrinsicTag(IntrinsicTagNames.name, "test-metric")
                        .build())
                .time(Instant.now())
                .value(42.0d)
                .build();
        timeSeriesStorage.store(Arrays.asList(sample));
        return null;
    }
}
