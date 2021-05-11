package org.opennms.timeseries.impl.influxdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.opennms.integration.api.v1.timeseries.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.influxdb.client.write.Point;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * We implement our own write logic with OKClient in order to let our caller handle backpressure.
 */
public class InfluxdbWriter {

    private static final Logger LOG = LoggerFactory.getLogger(InfluxdbWriter.class);

    final InfluxdbConfig config;
    private final OkHttpClient client;

    private final MetricRegistry metrics = new MetricRegistry();
    private final Meter samplesWritten = metrics.meter("samplesWritten");
    private final Meter samplesLost = metrics.meter("samplesLost");

    private final Bulkhead asyncHttpCallsBulkhead;

    public InfluxdbWriter(final InfluxdbConfig config) {
        this.config = config;
        ConnectionPool connectionPool = new ConnectionPool(config.getMaxConcurrentHttpConnections(), 5, TimeUnit.MINUTES);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(config.getMaxConcurrentHttpConnections());
        dispatcher.setMaxRequestsPerHost(config.getMaxConcurrentHttpConnections());

        this.client = new OkHttpClient.Builder()
                .readTimeout(config.getReadTimeoutInMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getWriteTimeoutInMs(), TimeUnit.MILLISECONDS)
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .build();


        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(config.getMaxConcurrentHttpConnections() * 6)
                .maxWaitDuration(Duration.ofMillis(config.getBulkheadMaxWaitDurationInMs()))
                .fairCallHandlingStrategyEnabled(true)
                .build();
        asyncHttpCallsBulkhead = Bulkhead.of("asyncHttpCalls", bulkheadConfig);

        // Expose HTTP client statistics
        metrics.register("connectionCount", (Gauge<Integer>) () -> client.connectionPool().connectionCount());
        metrics.register("idleConnectionCount", (Gauge<Integer>) () -> client.connectionPool().idleConnectionCount());
        metrics.register("queuedCallsCount", (Gauge<Integer>) () -> client.dispatcher().queuedCallsCount());
        metrics.register("runningCallsCount", (Gauge<Integer>) () -> client.dispatcher().runningCallsCount());
        metrics.register("availableConcurrentCalls", (Gauge<Integer>) () -> asyncHttpCallsBulkhead.getMetrics().getAvailableConcurrentCalls());
        metrics.register("maxAllowedConcurrentCalls", (Gauge<Integer>) () -> asyncHttpCallsBulkhead.getMetrics().getMaxAllowedConcurrentCalls());
    }

    public void writePoints(final List<Point> points) {
        asyncHttpCallsBulkhead.executeCompletionStage(() -> writeAsync(points)).whenComplete((r, ex) -> {
            if (ex == null) {
                samplesWritten.mark(points.size());
            } else {
                // FIXME: Data loss
                samplesLost.mark(points.size());
                LOG.error("Error occurred while storing samples, sample will be lost.", ex);
            }
        });
    }

    public CompletableFuture<Void> writeAsync(List<Point> points) {

        String bodyContent = points
                .stream()
                .map(Point::toLineProtocol)
                .collect(Collectors.joining("\n"));


        final CompletableFuture<Void> future = new CompletableFuture<>();


        final byte[] compressed;
        try {
            compressed = compress(bodyContent);
        } catch (IOException e) {
            future.completeExceptionally(e);
            return future;
        }
        final RequestBody body = RequestBody.create(bodyContent.getBytes(StandardCharsets.UTF_8));


        // TODO: Patrick make URL composition nicer
        String url = config.getUrl()
                + "/api/v2/write?org="
                + config.getOrg()
                + "&bucket="
                + config.getBucket()
                + "&precision=ms";

        final Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Encoding", "identity")
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("User-Agent", InfluxdbWriter.class.getCanonicalName())
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Token " + config.getToken())
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    String bodyAsString;
                    try (ResponseBody body = response.body()) {
                        bodyAsString = body.string();
                    } catch (IOException e) {
                        bodyAsString = "(error reading body)";
                    }

                    future.completeExceptionally(new StorageException(String.format("Writing to Influxdb failed: %s - %s: %s",
                            response.code(),
                            response.message(),
                            bodyAsString)));
                } else {
                    future.complete(null);
                }
            }
        });
        return future;
    }

    byte[] compress(final String stringToCompress) throws IOException {
        Objects.requireNonNull(stringToCompress);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
        gzip.write(stringToCompress.getBytes(StandardCharsets.UTF_8));
        gzip.close();
        return outputStream.toByteArray();
    }
}
