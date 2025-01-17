/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Mariana Cedica
 */
package org.nuxeo.ecm.core.storage.sql;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.management.api.ProbeInfo;
import org.nuxeo.ecm.core.management.api.ProbeManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.runtime.management",
        "org.nuxeo.ecm.core.management",
        "org.nuxeo.ecm.core.storage.binarymanager.s3" })
public class TestS3BinaryManagerStatus {

    @Inject
    ProbeManager pm;

    S3BinaryManager binaryManager;

    @Before
    public void initBinaryManager() throws IOException {
        binaryManager = new S3BinaryManager();
    }

    @Test
    public void testS3BinaryManagerStatus() {
        assertNotNull(binaryManager);
        ProbeInfo probeInfo = pm.getProbeInfo("s3BinaryManagerStatus");
        assertNotNull(probeInfo);
        pm.runProbe(probeInfo);
        // should fail since S3BinaryManager is not configured
        assertTrue(probeInfo.getStatus().isFailure());

    }
}
