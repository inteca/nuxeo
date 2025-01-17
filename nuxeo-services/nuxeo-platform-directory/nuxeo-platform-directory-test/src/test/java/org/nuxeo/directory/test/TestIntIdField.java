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
 *     Funsho David
 *
 */
package org.nuxeo.directory.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 9.2
 */
@RunWith(FeaturesRunner.class)
@Features(DirectoryFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@LocalDeploy("org.nuxeo.ecm.directory.tests:intIdDirectory-contrib.xml")
public class TestIntIdField {

    protected static final String INT_ID_DIRECTORY = "testIdDirectory";

    @Inject
    protected DirectoryService directoryService;

    @SuppressWarnings("boxing")
    @Test
    public void testIntIdDirectory() throws Exception {
        try (Session session = directoryService.open(INT_ID_DIRECTORY)) {
            Map<String, Object> map = createMapEntry(1, "toto");
            DocumentModel entry = session.createEntry(map);
            assertNotNull(entry);

            map = createMapEntry(2, "titi");
            DocumentModel entry2 = session.createEntry(map);
            assertNotNull(entry2);

            assertNotNull(session.getEntry("1"));
            assertNotNull(session.getEntry("2"));
            assertNull(session.getEntry("3"));
        }
    }

    /**
     * @since 11.1
     */
    @Test
    public void testIntIdCreateUpdateDelete() {
        try (Session session = directoryService.open(INT_ID_DIRECTORY)) {
            session.createEntry(createMapEntry(1, "toto"));

            DocumentModel entry = session.getEntry("1");
            assertNotNull(entry);
            assertEquals("toto", entry.getPropertyValue("label"));

            try {
                session.createEntry(createMapEntry(1, "toto"));
                fail("An exception should have been thrown");
            } catch (DirectoryException e) {
                assertEquals("Entry with id 1 already exists", e.getMessage());
            }

            entry.setPropertyValue("label", "titi");
            session.updateEntry(entry);
            entry = session.getEntry("1");
            assertEquals("titi", entry.getPropertyValue("label"));

            assertTrue(session.hasEntry("1"));
            session.deleteEntry("1");
            assertFalse(session.hasEntry("1"));
        }
    }

    protected Map<String, Object> createMapEntry(int id, String label) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("label", label);
        return map;
    }

}
