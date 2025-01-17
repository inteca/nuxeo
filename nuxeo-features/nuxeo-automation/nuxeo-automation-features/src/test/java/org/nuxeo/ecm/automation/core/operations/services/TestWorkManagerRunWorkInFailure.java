/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.automation.core.operations.services;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.operations.services.workmanager.WorkManagerRunWorkInFailure;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.SleepWork;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, CoreFeature.class })
@Deploy({ "org.nuxeo.runtime.stream", "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.features",
        "org.nuxeo.ecm.core.event:test-work-dead-letter-queue.xml" })
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestWorkManagerRunWorkInFailure {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService service;

    @Test
    public void testWorkManagerRunWorkInFailureOperation() throws Exception {
        // Submit a work that fails and goes to the dead letter queue
        Work failWork = new FailingWork(10);
        WorkManager wm = Framework.getService(WorkManager.class);
        wm.schedule(failWork);
        assertTrue(wm.awaitCompletion(2000, TimeUnit.MILLISECONDS));

        // Run the operation to replay the work in failure
        Map<String, Serializable> params = new HashMap<>();

        OperationContext ctx = new OperationContext(session);
        JSONBlob result = (JSONBlob) service.run(ctx, WorkManagerRunWorkInFailure.ID, params);
        assertNotNull(result);
        // One work re-processed without success
        JSONAssert.assertEquals("{\"total\":1,\"success\":0}", result.getString(), true);

        // Run it again
        result = (JSONBlob) service.run(ctx, WorkManagerRunWorkInFailure.ID, params);
        assertNotNull(result);
        // No work re-processed
        JSONAssert.assertEquals("{\"total\":0,\"success\":0}", result.getString(), true);
    }

    protected static class FailingWork extends SleepWork {
        private static final long serialVersionUID = 1L;

        public FailingWork(long durationMillis) {
            super(durationMillis);
        }

        @Override
        public void work() {
            super.work();
            throw new RuntimeException("Simulated failure in work: " + getId());
        }
    }
}
