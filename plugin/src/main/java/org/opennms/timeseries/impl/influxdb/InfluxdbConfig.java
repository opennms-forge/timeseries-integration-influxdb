package org.opennms.timeseries.impl.influxdb;

import java.util.Objects;
import java.util.StringJoiner;

public class InfluxdbConfig {
    private final String bucket;
    private final String org;
    private final String token;
    private final String url;
    private final boolean allowBackpressure;
    private final int maxConcurrentHttpConnections;
    private final long writeTimeoutInMs;
    private final long readTimeoutInMs;
    private final long bulkheadMaxWaitDurationInMs;

    public InfluxdbConfig(Builder builder) {
        this.bucket = Objects.requireNonNull(builder.bucket);
        this.org = Objects.requireNonNull(builder.org);
        this.token = Objects.requireNonNull(builder.token);
        this.url = Objects.requireNonNull(builder.url);
        this.allowBackpressure = builder.allowBackpressure;
        this.maxConcurrentHttpConnections = builder.maxConcurrentHttpConnections;
        this.writeTimeoutInMs = builder.writeTimeoutInMs;
        this.readTimeoutInMs = builder.readTimeoutInMs;
        this.bulkheadMaxWaitDurationInMs = builder.bulkheadMaxWaitDurationInMs;
    }

    /** Will be called via blueprint. The builder can be called when not running as Osgi plugin. */
    public InfluxdbConfig(
            final String bucket,
            final String org,
            final String token,
            final String url,
            final boolean allowBackpressure,
            final int maxConcurrentHttpConnections,
            final long writeTimeoutInMs,
            final long readTimeoutInMs,
            final long bulkheadMaxWaitDurationInMs) {
        this(builder()
                .bucket(bucket)
                .org(org)
                .token(token)
                .url(url)
                .allowBackpressure(allowBackpressure)
                .maxConcurrentHttpConnections(maxConcurrentHttpConnections)
                .writeTimeoutInMs(writeTimeoutInMs)
                .readTimeoutInMs(readTimeoutInMs)
                .bulkheadMaxWaitDurationInMs(bulkheadMaxWaitDurationInMs));
    }

    public String getBucket() {
        return bucket;
    }

    public String getOrg() {
        return org;
    }

    public String getToken() {
        return token;
    }

    public String getUrl() {
        return url;
    }

    public boolean isAllowBackpressure() {
        return allowBackpressure;
    }

    public int getMaxConcurrentHttpConnections() {
        return maxConcurrentHttpConnections;
    }

    public long getWriteTimeoutInMs() {
        return writeTimeoutInMs;
    }

    public long getReadTimeoutInMs() {
        return readTimeoutInMs;
    }

    public long getBulkheadMaxWaitDurationInMs() {
        return bulkheadMaxWaitDurationInMs;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", InfluxdbConfig.class.getSimpleName() + "[", "]")
                .add("bucket='" + bucket + "'")
                .add("org='" + org + "'")
                .add("token='" + token + "'")
                .add("url='" + url + "'")
                .add("allowBackpressure=" + allowBackpressure)
                .add("maxConcurrentHttpConnections=" + maxConcurrentHttpConnections)
                .add("writeTimeoutInMs=" + writeTimeoutInMs)
                .add("readTimeoutInMs=" + readTimeoutInMs)
                .add("bulkheadMaxWaitDurationInMs=" + bulkheadMaxWaitDurationInMs)
                .toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public final static class Builder {

        private String bucket = "opennms";
        private String org = "opennms";
        private String token = null;
        private String url = "http://localhost:8086";
        private boolean allowBackpressure = true;

        private int maxConcurrentHttpConnections = 100;
        private long writeTimeoutInMs = 1000;
        private long readTimeoutInMs = 1000;
        private long bulkheadMaxWaitDurationInMs = Long.MAX_VALUE;

        public Builder bucket(final String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder org(final String org) {
            this.org = org;
            return this;
        }

        public Builder token(final String token) {
            this.token = token;
            return this;
        }

        public Builder allowBackpressure(final boolean allowBackpressure) {
            this.allowBackpressure = allowBackpressure;
            return this;
        }

        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        public Builder maxConcurrentHttpConnections(final int maxConcurrentHttpConnections) {
            this.maxConcurrentHttpConnections = maxConcurrentHttpConnections;
            return this;
        }

        public Builder writeTimeoutInMs(final long writeTimeoutInMs) {
            this.writeTimeoutInMs = writeTimeoutInMs;
            return this;
        }

        public Builder readTimeoutInMs(final long readTimeoutInMs) {
            this.readTimeoutInMs = readTimeoutInMs;
            return this;
        }

        public Builder bulkheadMaxWaitDurationInMs(final long bulkheadMaxWaitDurationInMs) {
            this.bulkheadMaxWaitDurationInMs = bulkheadMaxWaitDurationInMs;
            return this;
        }

        public InfluxdbConfig build() {
            return new InfluxdbConfig(this);
        }
    }
}
