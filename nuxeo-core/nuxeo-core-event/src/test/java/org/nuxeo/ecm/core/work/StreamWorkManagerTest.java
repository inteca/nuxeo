/*
 * (C) Copyright 2017-2018 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bdelbosc
 */
package org.nuxeo.ecm.core.work;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkQueueMetrics;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.FileEventsTrackingFeature;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Adapt the tests with the limitation of the stream impl.
 *
 * @since 9.3
 */
@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, FileEventsTrackingFeature.class })
@Deploy({ "org.nuxeo.runtime.kv", "org.nuxeo.runtime.stream", "org.nuxeo.ecm.core.event",
        "org.nuxeo.ecm.core.event.test:test-workmanager-config.xml" })
@LocalDeploy("org.nuxeo.ecm.core.event:test-stream-workmanager-service.xml")
public class StreamWorkManagerTest extends WorkManagerTest {

    @Override
    protected void assertState(Work.State state, SleepWork work) {
        // Stream workmanager can not access scheduled work so no assertion are possible on state
    }

    @Override
    void assertMetrics(long scheduled, long running, long completed, long cancelled) {
        WorkQueueMetrics current = service.getMetrics(QUEUE);
        assertEquals("completed", completed, current.completed.longValue());
        // stream workmanager has only an estimation of the max running
        assertTrue("running", running <= current.running.longValue());
        assertEquals("scheduled or running", scheduled + running, current.scheduled.longValue());
        // stream workmanager has no canceled metrics
    }

    @Override
    void assertWorkIdsEquals(List<String> expected, Work.State state) {
        // we can not get a list of work id with the stream impl
    }

    @Override
    @Ignore()
    @Test
    public void testWorkManagerConfigDisableOneAfterStart() throws Exception {
        // for now processor can not be enable/disable once started
        super.testWorkManagerConfigDisableOneAfterStart();
    }

    @Override
    @Ignore()
    @Test
    public void testWorkManagerConfigDisableAllAfterStart() throws Exception {
        // for now processor can not be enable/disable once started
        super.testWorkManagerConfigDisableAllAfterStart();
    }

    @Test
    public void testWorkIdempotent() throws InterruptedException {
        MetricsTracker tracker = new MetricsTracker();
        SleepWork work = new SleepWork(1000, false);
        assertTrue(work.isIdempotent());
        service.schedule(work);
        assertTrue(service.awaitCompletion(5, TimeUnit.SECONDS));
        tracker.assertDiff(0, 0, 1, 0);

        // schedule again the exact same work 5 times
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);

        // works with the same id are skipped immediately and marked as completed, we don't have to wait 5s
        assertTrue(service.awaitCompletion(500, TimeUnit.MILLISECONDS));
        tracker.assertDiff(0, 0, 6, 0);
    }

    /**
     * This test cannot be run with storeState enabled.<br>
     * When the first work finishes, its status is not scheduled anymore and following works are not run.
     */
    @Test
    @Deploy("org.nuxeo.ecm.core.event:test-stream-workmanager-disable-storestate.xml")
    public void testWorkNonIdempotent() throws InterruptedException {
        MetricsTracker tracker = new MetricsTracker();
        SleepWork work = new SleepWork(1000, false);
        work.setIdempotent(false);
        assertFalse(work.isIdempotent());
        service.schedule(work);
        assertTrue(service.awaitCompletion(5, TimeUnit.SECONDS));
        tracker.assertDiff(0, 0, 1, 0);

        // schedule again the exact same work 5 times
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);
        service.schedule(work);

        // works with the same id are not skipped we need to wait more
        assertFalse(service.awaitCompletion(500, TimeUnit.MILLISECONDS));

        assertTrue(service.awaitCompletion(10, TimeUnit.SECONDS));
        tracker.assertDiff(0, 0, 6, 0);
    }

    @Test
    public void testWorkIdempotentConcurrent() throws InterruptedException {
        MetricsTracker tracker = new MetricsTracker();
        tracker.assertDiff(0, 0, 0, 0);
        SleepWork work1 = new SleepWork(1000, false);
        SleepWork work2 = new SleepWork(1000, false);
        service.schedule(work1);
        service.schedule(work1);
        service.schedule(work1);
        service.schedule(work2);
        service.schedule(work2);
        service.schedule(work2);
        // we don't know if work1 and work2 are executed on the same thread
        // but we assume that the max duration is work1 + work2 because there is only one invocation of each
        assertTrue(service.awaitCompletion(2500, TimeUnit.MILLISECONDS));
        tracker.assertDiff(0, 0, 6, 0);
    }

    @Override
    @Ignore()
    @Test
    public void testNoConcurrentJobsWithSameId() throws Exception {
        // default workmanager guaranty that works with the same id can not be scheduled while another is running
        // stream impl provides stronger guaranty, works with same id are executed only once (scheduled, running or
        // completed)
        super.testNoConcurrentJobsWithSameId();
    }

    @Test
    public void testCoalescingWorks() throws InterruptedException {
        MetricsTracker tracker = new MetricsTracker();
        long duration = 1000;
        // long work, to serve as a filler
        SleepWork longWork = new SleepWork(duration * 10);
        longWork.setIdempotent(false);
        longWork.setCoalescing(true);
        assertFalse(longWork.isIdempotent());
        assertTrue(longWork.isCoalescing());

        // short work the only to be actually computed
        SleepWork shortWork = new SleepWork(duration, false, longWork.getId());
        shortWork.setIdempotent(false);
        shortWork.setCoalescing(true);
        assertFalse(shortWork.isIdempotent());
        assertTrue(shortWork.isCoalescing());

        // we have to let the service warm up as the first offset is falsely set to 0
        service.schedule(shortWork);
        assertTrue(service.awaitCompletion(duration * 2, TimeUnit.MILLISECONDS));
        tracker.assertDiff(0, 0, 1, 0);
        // a work will actually be executed only if handled before the next one is scheduled
        // it's not the case here and the long works will be skipped
        service.schedule(longWork);
        service.schedule(longWork);
        // only the last, short work, will actually be computed and waiting for it's execution time is enough
        service.schedule(shortWork);
        assertTrue(service.awaitCompletion(duration * 2, TimeUnit.MILLISECONDS));
        tracker.assertDiff(0, 0, 4, 0);

        // if we wait a bit for the first one to be started, first and last works will be computed
        service.schedule(shortWork);
        Thread.sleep(500);
        service.schedule(longWork);
        service.schedule(shortWork);
        assertTrue(service.awaitCompletion(duration * 2, TimeUnit.MILLISECONDS));
        tracker.assertDiff(0, 0, 7, 0);
    }

}
