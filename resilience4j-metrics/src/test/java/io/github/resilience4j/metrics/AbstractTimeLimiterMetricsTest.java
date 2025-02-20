/*
 * Copyright 2019 Ingyu Hwang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.resilience4j.metrics;

import com.codahale.metrics.MetricRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static io.github.resilience4j.metrics.assertion.MetricRegistryAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.then;

public abstract class AbstractTimeLimiterMetricsTest {
    protected static final String DEFAULT_PREFIX = "resilience4j.timelimiter.UNDEFINED.";
    protected static final String SUCCESSFUL = "successful";
    protected static final String FAILED = "failed";
    protected static final String TIMEOUT = "timeout";

    protected MetricRegistry metricRegistry;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
    }

    protected abstract TimeLimiter given(String prefix, MetricRegistry metricRegistry);

    protected abstract TimeLimiter given(MetricRegistry metricRegistry);

    @Test
    public void shouldRegisterMetrics() throws Exception {
        TimeLimiter timeLimiter = given(metricRegistry);
        String expectedPrefix = "resilience4j.timelimiter.testLimit.";
        Supplier<CompletableFuture<String>> futureSupplier = () ->
                CompletableFuture.completedFuture("Hello world");

        String result = timeLimiter.decorateFutureSupplier(futureSupplier).call();

        then(result).isEqualTo("Hello world");
        assertThat(metricRegistry).hasMetricsSize(3);
        assertThat(metricRegistry).counter(expectedPrefix + SUCCESSFUL)
                .hasValue(1L);
        assertThat(metricRegistry).counter(expectedPrefix + FAILED)
                .hasValue(0L);
        assertThat(metricRegistry).counter(expectedPrefix + TIMEOUT)
                .hasValue(0L);
    }

    @Test
    public void shouldUseCustomPrefix() throws Exception {
        TimeLimiter timeLimiter = given("testPre", metricRegistry);
        String expectedPrefix = "testPre.testLimit.";
        Supplier<CompletableFuture<String>> futureSupplier = () ->
                CompletableFuture.completedFuture("Hello world");

        String result = timeLimiter.decorateFutureSupplier(futureSupplier).call();

        then(result).isEqualTo("Hello world");
        assertThat(metricRegistry).hasMetricsSize(3);
        assertThat(metricRegistry).counter(expectedPrefix + SUCCESSFUL)
                .hasValue(1L);
        assertThat(metricRegistry).counter(expectedPrefix + FAILED)
                .hasValue(0L);
        assertThat(metricRegistry).counter(expectedPrefix + TIMEOUT)
                .hasValue(0L);
    }

}
