/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.timeseries.impl.influxdb;

import static org.opennms.timeseries.impl.influxdb.TransformUtil.getIfMatching;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.influxdb.dto.QueryResult;
import org.opennms.integration.api.v1.timeseries.Sample;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableSample;

/**
 * Extracts and converts a QueryResult.Series to a list of Samples.
 * QueryResult.Series consists of a list of column names and a list of lists of values.
 */
public class SampleExtractor {

    private final Iterator<List<Object>> values;
    private List<Object> currentValues;
    private final List<String> tagNames;
    private final Map<String, Integer> columnIndexes;

    public SampleExtractor (QueryResult.Series series) {
        Objects.requireNonNull(series, "QueryResult.Series can not be null");
        this.tagNames = series.getColumns().stream()
                .filter(s -> !"value".equals(s))
                .filter(s -> !"time".equals(s))
                .collect(Collectors.toList());
        this.values = series.getValues().iterator();
        this.columnIndexes = new HashMap<>();
        for(int i = 0; i < series.getColumns().size(); i++) {
            columnIndexes.put(series.getColumns().get(i), i);
        }
    }

    public List<Sample> toSamples() {
        final List<Sample> samples = new ArrayList<>();
        while(this.values.hasNext()) {
            this.currentValues = this.values.next();
            Sample sample = getSample();
            samples.add(sample);
        }
        return samples;
    }

    private Sample getSample() {

        // Build Metric
        ImmutableMetric.MetricBuilder metric = ImmutableMetric.builder();
        for(String rawTagKey : this.tagNames) {
            String rawTagValue = (String) this.getValue(rawTagKey);
            getIfMatching(ImmutableMetric.TagType.intrinsic, rawTagKey, rawTagValue).ifPresent(metric::intrinsicTag);
            getIfMatching(ImmutableMetric.TagType.meta, rawTagKey, rawTagValue).ifPresent(metric::metaTag);
        }

        // Build Sample
        final Instant time = Instant.parse((String)getValue("time"));
        final Double value =  (Double)getValue("value");
        return ImmutableSample.builder()
                .metric(metric.build())
                .time(time)
                .value(value)
                .build();
    }

    private Object getValue(final String valueName) {
        int index = this.columnIndexes.get(valueName);
        return this.currentValues.get(index);
    }
}
