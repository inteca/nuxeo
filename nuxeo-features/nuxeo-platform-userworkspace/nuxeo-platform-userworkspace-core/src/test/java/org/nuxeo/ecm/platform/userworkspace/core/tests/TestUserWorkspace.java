/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.userworkspace.core.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.trash.TrashService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.platform.userworkspace.core.service.AbstractUserWorkspaceImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.userworkspace.api", //
        "org.nuxeo.ecm.platform.userworkspace.types", //
        "org.nuxeo.ecm.platform.userworkspace.core", //
})
public class TestUserWorkspace {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected UserWorkspaceService uwm;

    @Inject
    protected PathSegmentService pathSegments;

    @Inject
    protected UserManager userManager;

    @Test
    public void testRestrictedAccess() throws Exception {
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, null);
            assertNotNull(uw);

            // check creator
            String creator = (String) uw.getProperty("dublincore", "creator");
            assertEquals("toto", creator);

            // check write access
            uw.setProperty("dublibore", "description", "Toto's workspace");
            userSession.saveDocument(uw);
            userSession.save();
        }
    }

    @Test
    public void testMultiDomains() throws Exception {
        ACE ace = new ACE("Everyone", "Read", true);
        ACL acl = new ACLImpl();
        acl.add(ace);
        ACP acp = new ACPImpl();
        acp.addACL(acl);

        DocumentModel ws1 = session.createDocumentModel("/default-domain/workspaces", "ws1", "Workspace");
        ws1 = session.createDocument(ws1);
        ws1.setACP(acp, true);
        ws1 = session.saveDocument(ws1);

        DocumentModel alternate = session.createDocumentModel("/", "alternate-domain", "Domain");
        alternate = session.createDocument(alternate);
        DocumentModel ws2 = session.createDocumentModel("/alternate-domain/workspaces", "ws2", "Workspace");
        ws2 = session.createDocument(ws2);
        ws2.setACP(acp, true);
        ws2 = session.saveDocument(ws2);

        session.save();

        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            // access from root
            DocumentModel context = userSession.getRootDocument();
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, null);
            assertNotNull(uw);
            assertTrue(uw.getPathAsString().startsWith("/default-domain"));

            // access form default domain
            context = userSession.getDocument(ws1.getRef());
            uw = uwm.getCurrentUserPersonalWorkspace(userSession, context);
            assertNotNull(uw);
            assertTrue(uw.getPathAsString().startsWith("/default-domain"));

            // access form alternate domain
            context = userSession.getDocument(ws2.getRef());
            uw = uwm.getCurrentUserPersonalWorkspace(userSession, context);
            assertNotNull(uw);
            assertTrue(uw.getPathAsString(), uw.getPathAsString().startsWith("/default-domain"));

            // now delete the default-domain
            session.removeDocument(new PathRef("/default-domain"));
            session.save();
            userSession.save();
            uw = uwm.getCurrentUserPersonalWorkspace(userSession, context);
            assertNotNull(uw);
            assertTrue(uw.getPathAsString().startsWith("/alternate-domain"));
        }
    }

    @Test
    public void testAnotherUserWorkspaceFinder() {
        DocumentModel context = session.getRootDocument();
        DocumentModel uw = uwm.getCurrentUserPersonalWorkspace("user1", context);
        session.save();

        assertNotNull(uw);
        String user1WorkspacePath = uw.getPathAsString();
        assertTrue(user1WorkspacePath.contains("user1"));

        session.save();

        try (CoreSession userSession = coreFeature.openCoreSession("user2")) {
            context = userSession.getRootDocument();
            try {
                // Assert that it throws
                uwm.getCurrentUserPersonalWorkspace("user1", context);
                assertTrue("user2 is not allow to read user1 workspace", false);
            } catch (DocumentSecurityException e) {
                // Nothing to do
            }
            uw = uwm.getUserPersonalWorkspace("user1", context);
            assertNotNull(uw);
            assertTrue(uw.getPathAsString().contains("user1"));
            assertEquals(user1WorkspacePath, uw.getPathAsString());
            assertNull("Document is correctly detached", uw.getSessionId());
        }
    }

    @Test
    public void testPathSegmentMapping() {
        DocumentModel context = session.getRootDocument();

        // Automatically create the user workspace
        DocumentModel uw = uwm.getUserPersonalWorkspace("AC/DC", context);

        assertNotNull(uw);
        // Check the document name was mapped
        assertEquals(uw.getPath().lastSegment(), "AC~2fDC");
    }

    @Test
    public void testWorkspaceNameCollision() {
        try (CoreSession userSession = coreFeature.openCoreSession(alongname("user1"))) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, userSession.getRootDocument());
            assertNotNull(uw);
        }
        try (CoreSession userSession = coreFeature.openCoreSession(alongname("user2"))) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, userSession.getRootDocument());
            assertNotNull(uw);
        }
    }

    String alongname(String name) {
        return StringUtils.repeat("a", pathSegments.getMaxSize()).concat(name);
    }

    @Test
    public void testUserWorkspaceFinderCompat() {
        // Manually create the user workspace as if the old max size still stands
        DocumentModel context = session.getRootDocument();
        DocumentModel user1W = uwm.getCurrentUserPersonalWorkspace("user1", context);
        String parentPath = user1W.getPathAsString().replace("/user1", "");
        DocumentModel uw = session.createDocumentModel(parentPath, "John-Von-Verylonglastname", "Workspace");
        uw = session.createDocument(uw);
        ACP acp = new ACPImpl();
        ACE grantEverything = new ACE("John Von Verylonglastname", SecurityConstants.EVERYTHING, true);
        ACL acl = new ACLImpl();
        acl.setACEs(new ACE[] { grantEverything });
        acp.addACL(acl);
        uw.setACP(acp, true);
        assertTrue(uw.getPath().lastSegment().length() > pathSegments.getMaxSize());
        session.save();

        assertNotNull(uw);
        String johnWorkspacePath = uw.getPathAsString();
        assertTrue(johnWorkspacePath.endsWith("/John-Von-Verylonglastname"));

        uw = uwm.getUserPersonalWorkspace("John Von Verylonglastname", context);
        assertNotNull(uw);
        // Check the user workspace with the old name format is retrieved
        assertTrue(uw.getPathAsString().endsWith("/John-Von-Verylonglastname"));
        assertEquals(johnWorkspacePath, uw.getPathAsString());
        assertNull("Document is correctly detached", uw.getSessionId());
    }

    @Test
    public void testUnrestrictedFinderCorrectlyCreateWorkspace() {
        DocumentModel context = session.getRootDocument();
        DocumentModel uw = uwm.getUserPersonalWorkspace("user1", context);
        assertNotNull(uw);
        String user1WorkspacePath = uw.getPathAsString();

        session.save();

        try (CoreSession userSession = coreFeature.openCoreSession("user1")) {
            context = userSession.getRootDocument();

            uw = uwm.getCurrentUserPersonalWorkspace(userSession, context);
            assertNotNull(uw);
            assertEquals(user1WorkspacePath, uw.getPathAsString());
        }
    }

    @Test
    public void testCandidateNames() {
        expectCandidateNames("user", //
                "user");
        expectCandidateNames("Dr. John Doe", //
                "Dr. John Doe", //
                "Dr-John-Doe");
        expectCandidateNames("user@example.com", //
                "user~40example.com", //
                "user-example-com");
        expectCandidateNames("a/b@c~d?f&g", //
                "a~2fb~40c~7ed~3ff~26g", //
                "a-b-c-d-f-g");
        // 23 chars
        expectCandidateNames("useruseruseruseruseruse", //
                "useruseruseruseruseruse");
        // 24 chars
        expectCandidateNames("useruseruseruseruseruser", //
                "useruseruseruseruseruser", //
                "useruseruseruser37fcb8c6");
        // 26 chars
        expectCandidateNames("useruseruseruseruseruserus", //
                "useruseruseruseruseruserus", //
                "useruseruseruseruseruser", //
                "useruseruserusercc1f8605");
        // 30 chars
        expectCandidateNames("useruseruseruseruseruseruserus", //
                "useruseruseruseruseruseruserus", //
                "useruseruseruseruseruser", //
                "useruseruseruserbe8cd76e", //
                "useruseruseruseruserusbe8cd76e");
        // 32 chars
        expectCandidateNames("useruseruseruseruseruseruseruser", //
                "useruseruseruseruseruseruseruser", //
                "useruseruseruseruseruser", //
                "useruseruseruser13980873", //
                "useruseruseruseruseruseruserus", //
                "useruseruseruseruserus13980873");
    }

    protected void expectCandidateNames(String username, String... expected) {
        assertEquals(Arrays.asList(expected),
                ((AbstractUserWorkspaceImpl) uwm).getCandidateUserWorkspaceNames(username));
    }

    @Test
    public void testIsUnderUserWorkspace() {
        doTestIsUnderUserWorkspace("user1");
    }

    @Test
    public void testIsUnderUserWorkspaceWithMangledName() {
        doTestIsUnderUserWorkspace("user1@example.com");
    }

    protected void doTestIsUnderUserWorkspace(String username) {
        DocumentModel foo = session.createDocumentModel("/", "foo" + username, "File");
        foo = session.createDocument(foo);
        ACP acp = new ACPImpl();
        acp.addACE(ACL.LOCAL_ACL, new ACE(username, SecurityConstants.READ, true));
        foo.setACP(acp, true);
        session.save();

        try (CoreSession userSession = coreFeature.openCoreSession(username)) {
            Principal principal = userSession.getPrincipal();
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, userSession.getRootDocument());
            DocumentModel bar = userSession.createDocumentModel(uw.getPathAsString(), "bar", "File");
            bar = userSession.createDocument(bar);
            userSession.save();

            assertTrue(uwm.isUnderUserWorkspace(principal, null, uw));
            assertTrue(uwm.isUnderUserWorkspace(principal, null, bar));

            assertFalse(uwm.isUnderUserWorkspace(principal, null, userSession.getRootDocument()));
            assertFalse(uwm.isUnderUserWorkspace(principal, null, userSession.getDocument(foo.getRef())));
        }
    }

    /**
     * @since 9.3
     */
    @Test
    public void testCannotRetrieveUserWorkspaceWithoutDomains() {
        List<DocumentRef> refs = session.getChildren(session.getRootDocument().getRef())
                                        .stream()
                                        .map(DocumentModel::getRef)
                                        .collect(Collectors.toList());
        session.removeDocuments(refs.toArray(new DocumentRef[refs.size()]));
        session.save();
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession, null);
            assertNull(uw);
        }
    }

    /**
     * @since 10.3
     */
    @Test
    public void testCanRetrieveUserWorkspaceWithTrashedDomain() {
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession);
            assertNotNull(uw);
        }
        List<DocumentModel> docs = session.getChildren(session.getRootDocument().getRef())
                                        .stream()
                                        .collect(Collectors.toList());
        TrashService trashService = Framework.getService(TrashService.class);
        trashService.trashDocuments(docs);
        session.save();
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession);
            assertNotNull(uw);
        }
        trashService.undeleteDocuments(docs);
        session.save();
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            DocumentModel uw = uwm.getCurrentUserPersonalWorkspace(userSession);
            assertNotNull(uw);
        }
    }

    /**
     * NXP-26003
     */
    @Test
    public void testGetUserPersonalWorkspaceWithSameCandidateNames() {
        try (CoreSession userSession = coreFeature.openCoreSession("toto")) {
            // use another user as #getUserPersonalWorkspace call is launching an UnrestrictedSessionRunner
            DocumentModel context = userSession.getRootDocument();
            // user a-b, candidate names will have "a-b"
            // this will create the "a-b" user workspace
            DocumentModel uw = uwm.getUserPersonalWorkspace("a-b", context);
            assertEquals("a-b", uw.getName());
            // user a_b, candidate names will have "a-b" also
            // "a-b" user workspace exists, but a_b user does not have permission on it
            uw = uwm.getUserPersonalWorkspace("a_b", context);
            assertEquals("a_b", uw.getName());
        }
    }

    /**
     * NXP-28841
     */
    @Test
    @LocalDeploy("org.nuxeo.ecm.platform.userworkspace.core:test-security-policy.xml")
    public void testGetUserPersonalWorkspaceFromPrivilegedSessionSecurityPolicy() {
        // create foo user
        DocumentModel user = userManager.getBareUserModel();
        user.setPropertyValue("username", "foo");
        userManager.createUser(user);
        // create foo user workspace
        uwm.getUserPersonalWorkspace("foo", session.getRootDocument());

        // fetch existing foo user workspace
        // will pass through PrivilegedSessionSecurityPolicy that will call again #getUserPersonalWorkspace
        DocumentModel uw = uwm.getUserPersonalWorkspace("foo", session.getRootDocument());
        assertNotNull(uw);
        assertEquals("foo", uw.getName());
    }

}
