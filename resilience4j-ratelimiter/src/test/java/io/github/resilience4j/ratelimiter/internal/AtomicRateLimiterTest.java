/*
 *
 *  Copyright 2016 Robert Winkler and Bohdan Storozhuk
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package io.github.resilience4j.ratelimiter.internal;

import com.jayway.awaitility.core.ConditionFactory;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AtomicRateLimiter.class)
public class AtomicRateLimiterTest {

    private static final String LIMITER_NAME = "test";
    private static final long CYCLE_IN_NANOS = 50_000_000L;
    private static final long POLL_INTERVAL_IN_NANOS = 2_000_000L;
    private static final int PERMISSIONS_RER_CYCLE = 1;
    private AtomicRateLimiter rateLimiter;
    private AtomicRateLimiter.AtomicRateLimiterMetrics metrics;

    private static ConditionFactory awaitImpatiently() {
        return await()
            .pollDelay(1, TimeUnit.MICROSECONDS)
            .pollInterval(POLL_INTERVAL_IN_NANOS, TimeUnit.NANOSECONDS);
    }

    private void setTimeOnNanos(long nanoTime) throws Exception {
        PowerMockito.doReturn(nanoTime)
            .when(rateLimiter, "currentNanoTime");
    }

    public void setup(Duration timeoutDuration) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(PERMISSIONS_RER_CYCLE)
            .limitRefreshPeriod(Duration.ofNanos(CYCLE_IN_NANOS))
            .timeoutDuration(timeoutDuration)
            .build();
        AtomicRateLimiter testLimiter = new AtomicRateLimiter(LIMITER_NAME, rateLimiterConfig);
        rateLimiter = PowerMockito.spy(testLimiter);
        metrics = rateLimiter.getDetailedMetrics();
    }

    @Test
    public void notSpyRawTest() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(PERMISSIONS_RER_CYCLE)
                .limitRefreshPeriod(Duration.ofNanos(CYCLE_IN_NANOS))
                .timeoutDuration(Duration.ZERO)
                .build();
        AtomicRateLimiter rawLimiter = new AtomicRateLimiter("rawLimiter", rateLimiterConfig);
        AtomicRateLimiter.AtomicRateLimiterMetrics rawDetailedMetrics = rawLimiter.getDetailedMetrics();

        long firstCycle = rawDetailedMetrics.getCycle();
        while (firstCycle == rawDetailedMetrics.getCycle()) {
            System.out.print('.'); // wait for current cycle to pass
        }

        boolean firstPermission = rawLimiter.acquirePermission();
        then(firstPermission).isTrue();

        long nanosToWait = rawDetailedMetrics.getNanosToWait();
        long startTime = System.nanoTime();
        while(System.nanoTime() - startTime < nanosToWait) {
            System.out.print('*'); // wait for permission renewal
        }

        boolean secondPermission = rawLimiter.acquirePermission();
        then(secondPermission).isTrue();

        boolean firstNoPermission = rawLimiter.acquirePermission();
        then(firstNoPermission).isFalse();
        long secondCycle = rawDetailedMetrics.getCycle();

        rawLimiter.changeLimitForPeriod(PERMISSIONS_RER_CYCLE * 2);
        nanosToWait = rawDetailedMetrics.getNanosToWait();
        startTime = System.nanoTime();
        while(System.nanoTime() - startTime < nanosToWait) {
            System.out.print('^'); // wait for permission renewal
        }
        boolean thirdPermission = rawLimiter.acquirePermission();
        then(thirdPermission).isTrue();

        boolean fourthPermission = rawLimiter.acquirePermission();
        then(fourthPermission).isTrue();

        boolean secondNoPermission = rawLimiter.acquirePermission();
        then(secondNoPermission).isFalse();
        long thirdCycle = rawDetailedMetrics.getCycle();


        then(secondCycle - firstCycle).isEqualTo(2);
        then(thirdCycle - secondCycle).isEqualTo(1);
    }

    @Test
    public void notSpyRawNonBlockingTest() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(PERMISSIONS_RER_CYCLE)
                .limitRefreshPeriod(Duration.ofNanos(CYCLE_IN_NANOS))
                .timeoutDuration(Duration.ZERO)
                .build();

        AtomicRateLimiter rawLimiter = new AtomicRateLimiter("rawLimiter", rateLimiterConfig);
        AtomicRateLimiter.AtomicRateLimiterMetrics rawDetailedMetrics = rawLimiter.getDetailedMetrics();

        long firstCycle = rawDetailedMetrics.getCycle();
        while (firstCycle == rawDetailedMetrics.getCycle()) {
            System.out.print('.'); // wait for current cycle to pass
        }

        long firstPermission = rawLimiter.reservePermission();
        long nanosToWait = rawDetailedMetrics.getNanosToWait();
        long startTime = System.nanoTime();
        while(System.nanoTime() - startTime < nanosToWait) {
            System.out.print('*'); // wait for permission renewal
        }

        long secondPermission = rawLimiter.reservePermission();
        long firstNoPermission = rawLimiter.reservePermission();
        long secondCycle = rawDetailedMetrics.getCycle();

        rawLimiter.changeLimitForPeriod(PERMISSIONS_RER_CYCLE * 2);
        nanosToWait = rawDetailedMetrics.getNanosToWait();
        startTime = System.nanoTime();
        while(System.nanoTime() - startTime < nanosToWait) {
            System.out.print('^'); // wait for permission renewal
        }
        long thirdPermission = rawLimiter.reservePermission();
        long fourthPermission = rawLimiter.reservePermission();
        long secondNoPermission = rawLimiter.reservePermission();
        long thirdCycle = rawDetailedMetrics.getCycle();


        then(secondCycle - firstCycle).isEqualTo(2);
        then(thirdCycle - secondCycle).isEqualTo(1);

        then(firstPermission).isZero();
        then(secondPermission).isZero();
        then(thirdPermission).isZero();
        then(fourthPermission).isZero();

        then(firstNoPermission).isNegative();
        then(secondNoPermission).isNegative();
    }

    @Test
    public void permissionsInFirstCycle() throws Exception {
        setup(Duration.ZERO);

        setTimeOnNanos(CYCLE_IN_NANOS - 10);
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        int availablePermissions = metrics.getAvailablePermissions();
        then(availablePermissions).isEqualTo(PERMISSIONS_RER_CYCLE);
    }

    @Test
    public void acquireAndRefreshWithEventPublishing() throws Exception {
        setup(Duration.ZERO);

        setTimeOnNanos(CYCLE_IN_NANOS);
        boolean permission = rateLimiter.acquirePermission();
        then(permission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        boolean secondPermission = rateLimiter.acquirePermission();
        then(secondPermission).isFalse();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);

        setTimeOnNanos(CYCLE_IN_NANOS * 2);
        boolean thirdPermission = rateLimiter.acquirePermission();
        then(thirdPermission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        boolean fourthPermission = rateLimiter.acquirePermission();
        then(fourthPermission).isFalse();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
    }

    @Test
    public void reserveAndRefresh() throws Exception {
        setup(Duration.ofNanos(CYCLE_IN_NANOS));

        setTimeOnNanos(CYCLE_IN_NANOS);
        boolean permission = rateLimiter.acquirePermission();
        then(permission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);

        AtomicReference<Boolean> reservedPermission = new AtomicReference<>(null);
        Thread caller = new Thread(
            () -> reservedPermission.set(rateLimiter.acquirePermission()));
        caller.setDaemon(true);
        caller.start();
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(caller::getState, equalTo(Thread.State.TIMED_WAITING));
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS + CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(1);

        setTimeOnNanos(CYCLE_IN_NANOS * 2 + 10);
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(reservedPermission::get, equalTo(true));

        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS - 10);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void reserveFewThenSkipCyclesBeforeRefreshNonBlocking() throws Exception {
        setup(Duration.ofNanos(CYCLE_IN_NANOS));

        setTimeOnNanos(CYCLE_IN_NANOS);
        long permission = rateLimiter.reservePermission();
        then(permission).isZero();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        long reservation = rateLimiter.reservePermission();
        then(reservation).isPositive();
        then(reservation).isLessThanOrEqualTo(CYCLE_IN_NANOS);
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        long additionalReservation = rateLimiter.reservePermission();
        then(additionalReservation).isEqualTo(-1);
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        setTimeOnNanos(CYCLE_IN_NANOS * 6 + 10);
        then(metrics.getAvailablePermissions()).isEqualTo(1);
        then(metrics.getNanosToWait()).isEqualTo(0L);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void reserveFewThenSkipCyclesBeforeRefresh() throws Exception {
        setup(Duration.ofNanos(CYCLE_IN_NANOS));

        setTimeOnNanos(CYCLE_IN_NANOS);
        boolean permission = rateLimiter.acquirePermission();
        then(permission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        AtomicReference<Boolean> firstReservedPermission = new AtomicReference<>(null);
        Thread firstCaller = new Thread(
            () -> firstReservedPermission.set(rateLimiter.acquirePermission()));
        firstCaller.setDaemon(true);
        firstCaller.start();
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(firstCaller::getState, equalTo(Thread.State.TIMED_WAITING));
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(1);

        AtomicReference<Boolean> secondReservedPermission = new AtomicReference<>(null);
        Thread secondCaller = new Thread(
            () -> secondReservedPermission.set(rateLimiter.acquirePermission()));
        secondCaller.setDaemon(true);
        secondCaller.start();
        awaitImpatiently()
                .atMost(5, SECONDS)
            .until(secondCaller::getState, equalTo(Thread.State.TIMED_WAITING));
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(2);

        setTimeOnNanos(CYCLE_IN_NANOS * 6 + 10);
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(firstReservedPermission::get, equalTo(true));
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(secondReservedPermission::get, equalTo(false));
        then(metrics.getAvailablePermissions()).isEqualTo(1);
        then(metrics.getNanosToWait()).isEqualTo(0L);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void rejectedByTimeoutNonBlocking() throws Exception {
        setup(Duration.ZERO);

        setTimeOnNanos(CYCLE_IN_NANOS);
        long permission = rateLimiter.reservePermission();
        then(permission).isZero();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        long failedPermission = rateLimiter.reservePermission();
        then(failedPermission).isNegative();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        setTimeOnNanos(CYCLE_IN_NANOS * 2 - 1);
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(1L);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void waitingThreadIsInterrupted() throws Exception {
        setup(Duration.ofNanos(CYCLE_IN_NANOS));

        setTimeOnNanos(CYCLE_IN_NANOS);
        boolean permission = rateLimiter.acquirePermission();
        then(permission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);

        AtomicReference<Boolean> reservedPermission = new AtomicReference<>(null);
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        Thread caller = new Thread(
            () -> {
                reservedPermission.set(rateLimiter.acquirePermission());
                wasInterrupted.set(Thread.currentThread().isInterrupted());
            }
        );
        caller.setDaemon(true);
        caller.start();

        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(caller::getState, equalTo(Thread.State.TIMED_WAITING));
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(1);

        caller.interrupt();
        awaitImpatiently()
            .atMost(5, SECONDS)
            .until(reservedPermission::get, equalTo(false));
        then(wasInterrupted.get()).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS * 2);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void changePermissionsLimitBetweenCycles() throws Exception {
        setup(Duration.ofNanos(CYCLE_IN_NANOS));

        setTimeOnNanos(CYCLE_IN_NANOS);
        boolean permission = rateLimiter.acquirePermission();
        then(permission).isTrue();
        then(metrics.getAvailablePermissions()).isEqualTo(0);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);

        AtomicReference<Boolean> reservedPermission = new AtomicReference<>(null);
        Thread caller = new Thread(
                () -> reservedPermission.set(rateLimiter.acquirePermission()));
        caller.setDaemon(true);
        caller.start();
        awaitImpatiently()
                .atMost(5, SECONDS)
                .until(caller::getState, equalTo(Thread.State.TIMED_WAITING));
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS + CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(1);

        rateLimiter.changeLimitForPeriod(PERMISSIONS_RER_CYCLE * 2);
        then(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(PERMISSIONS_RER_CYCLE * 2);
        then(metrics.getAvailablePermissions()).isEqualTo(-1);
        then(metrics.getNanosToWait()).isEqualTo(CYCLE_IN_NANOS);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(1);


        setTimeOnNanos(CYCLE_IN_NANOS * 2 + 10);
        awaitImpatiently()
                .atMost(5, SECONDS)
                .until(reservedPermission::get, equalTo(true));

        then(metrics.getAvailablePermissions()).isEqualTo(1);
        then(metrics.getNanosToWait()).isEqualTo(0);
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    public void changeDefaultTimeoutDuration() throws Exception {
        setup(Duration.ZERO);

        RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
        then(rateLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ZERO);
        then(rateLimiterConfig.getLimitForPeriod()).isEqualTo(PERMISSIONS_RER_CYCLE);
        then(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofNanos(CYCLE_IN_NANOS));

        rateLimiter.changeTimeoutDuration(Duration.ofSeconds(1));
        then(rateLimiterConfig != rateLimiter.getRateLimiterConfig()).isTrue();
        rateLimiterConfig = rateLimiter.getRateLimiterConfig();
        then(rateLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(1));
        then(rateLimiterConfig.getLimitForPeriod()).isEqualTo(PERMISSIONS_RER_CYCLE);
        then(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofNanos(CYCLE_IN_NANOS));
    }

    @Test
    public void changeLimitForPeriod() throws Exception {
        setup(Duration.ZERO);

        RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
        then(rateLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ZERO);
        then(rateLimiterConfig.getLimitForPeriod()).isEqualTo(PERMISSIONS_RER_CYCLE);
        then(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofNanos(CYCLE_IN_NANOS));

        rateLimiter.changeLimitForPeriod(35);
        then(rateLimiterConfig != rateLimiter.getRateLimiterConfig()).isTrue();
        rateLimiterConfig = rateLimiter.getRateLimiterConfig();
        then(rateLimiterConfig.getTimeoutDuration()).isEqualTo(Duration.ZERO);
        then(rateLimiterConfig.getLimitForPeriod()).isEqualTo(35);
        then(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofNanos(CYCLE_IN_NANOS));
    }

    @Test
    public void metricsTest() {
        setup(Duration.ZERO);

        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        then(metrics.getNumberOfWaitingThreads()).isEqualTo(0);
        then(metrics.getAvailablePermissions()).isEqualTo(1);

        AtomicRateLimiter.AtomicRateLimiterMetrics detailedMetrics = rateLimiter.getDetailedMetrics();
        then(detailedMetrics.getNumberOfWaitingThreads()).isEqualTo(0);
        then(detailedMetrics.getAvailablePermissions()).isEqualTo(1);
        then(detailedMetrics.getNanosToWait()).isEqualTo(0);
        then(detailedMetrics.getCycle()).isGreaterThan(0);
    }

    @Test
    public void namePropagation() {
        setup(Duration.ZERO);
        then(rateLimiter.getName()).isEqualTo(LIMITER_NAME);
    }

    @Test
    public void metrics() {
        setup(Duration.ZERO);
        then(rateLimiter.getMetrics().getNumberOfWaitingThreads()).isEqualTo(0);
    }
}
