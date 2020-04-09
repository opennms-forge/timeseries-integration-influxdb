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

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.influxdb.dto.QueryResult;
import org.junit.Test;
import org.opennms.integration.api.v1.timeseries.Metric;
import org.opennms.integration.api.v1.timeseries.Sample;
import org.opennms.integration.api.v1.timeseries.immutables.ImmutableMetric;

public class SampleExtractorTest {

    @Test
    public void shouldExtractSamples() {

        Instant now = Instant.now();
        Metric metric = ImmutableMetric.builder()
                .intrinsicTag("resourceId", "snmp:1:opennms-jvm:org_opennms_newts_name_ring_buffer_max_size")
                .intrinsicTag("name", "NewtsRingBufMaxSize")
                .metaTag("meta_idx0", "(snmp,4)")
                .build();
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList(now.minusMillis(1).toString(),
                "snmp:1:opennms-jvm:org_opennms_newts_name_ring_buffer_max_size",
                "NewtsRingBufMaxSize", "(snmp,4)",
                3.0));
        values.add(Arrays.asList(now.toString(),
                "snmp:1:opennms-jvm:org_opennms_newts_name_ring_buffer_max_size",
                "NewtsRingBufMaxSize", "(snmp,4)",
                1.0));
        QueryResult.Series series = new QueryResult.Series();
        series.setColumns(Arrays.asList("time", "intrinsic_resourceId", "intrinsic_name", "meta_idx0", "value"));
        series.setValues(values);
        List<Sample> samples = new SampleExtractor(series).toSamples();
        assertEquals(2, samples.size());

        assertEquals(metric, samples.get(0).getMetric());
        assertEquals(now.minusMillis(1), samples.get(0).getTime());
        assertEquals(Double.valueOf(3.0), samples.get(0).getValue());

        assertEquals(metric, samples.get(1).getMetric());
        assertEquals(now, samples.get(1).getTime());
        assertEquals(Double.valueOf(1.0), samples.get(1).getValue());
    }

}
