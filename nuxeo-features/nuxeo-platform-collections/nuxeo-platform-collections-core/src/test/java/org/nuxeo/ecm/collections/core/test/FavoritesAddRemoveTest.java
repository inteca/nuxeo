/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume</a>
 */
package org.nuxeo.ecm.collections.core.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.collections.api.FavoritesManager;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 5.9.4
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.userworkspace.core", "org.nuxeo.ecm.platform.collections.core",
        "org.nuxeo.ecm.platform.userworkspace.types", "org.nuxeo.ecm.platform.query.api",
        "org.nuxeo.ecm.platform.web.common", "org.nuxeo.ecm.platform.tag" })
public class FavoritesAddRemoveTest {

    protected static final String TEST_FILE_NAME = "testFile";

    @Inject
    FavoritesManager favoritesManager;

    @Inject
    CoreSession session;

    protected CoreSession userSession;

    protected DocumentModel testWorkspace;

    protected DocumentModel testFile;

    @Before
    public void setup() {
        testWorkspace = session.createDocumentModel("/default-domain/workspaces", "testWorkspace",
                "Workspace");
        testWorkspace = session.createDocument(testWorkspace);
        testFile = session.createDocumentModel(testWorkspace.getPathAsString(), TEST_FILE_NAME, "File");
        testFile = session.createDocument(testFile);
    }

    @Test
    public void addRemoveToFavoritesTest() {
        favoritesManager.addToFavorites(testFile, session);
        assertTrue(favoritesManager.isFavorite(testFile, session));

        favoritesManager.removeFromFavorites(testFile, session);
        assertFalse(favoritesManager.isFavorite(testFile, session));
    }

    @Test
    public void testFavoritesWithoutDomain() {
        favoritesManager.addToFavorites(testFile, session);
        assertTrue(favoritesManager.isFavorite(testFile, session));

        // remove the only domain
        session.removeDocument(new PathRef("/default-domain"));

        // no user favorites, always false
        assertFalse(favoritesManager.isFavorite(testFile, session));
        assertFalse(favoritesManager.isFavorite(testWorkspace, session));
    }
}
