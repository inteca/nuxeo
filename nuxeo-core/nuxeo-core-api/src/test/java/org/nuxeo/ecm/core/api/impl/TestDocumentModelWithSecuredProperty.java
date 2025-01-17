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
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.api.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.ReadOnlyPropertyException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class })
@Deploy({ "org.nuxeo.ecm.core.schema", "org.nuxeo.ecm.core.api.tests:OSGI-INF/dummy-login-config.xml",
        "org.nuxeo.ecm.core.api.tests:OSGI-INF/test-documentmodel-secured-types-contrib.xml" })
public class TestDocumentModelWithSecuredProperty {

    protected static final String SECURED_SCHEMA = "secured";

    @Test
    public void testSetSecuredScalarProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "scalar", "test secure");
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: scalar since it is readonly", e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredScalarPropertyWithSystem() {
        Framework.doPrivileged(() -> {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "scalar", "test secure");
            assertEquals("test secure", doc.getProperty(SECURED_SCHEMA, "scalar"));
        });
    }

    @Test
    public void testSetSecuredScalarPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "scalar", "test secure");
            assertEquals("test secure", doc.getProperty(SECURED_SCHEMA, "scalar"));
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetUnsecuredScalarProperty() {
        DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
        doc.setProperty(SECURED_SCHEMA, "unsecureScalar", "test unsecure");
        assertEquals("test unsecure", doc.getProperty(SECURED_SCHEMA, "unsecureScalar"));
    }

    @Test
    public void testSetSecuredComplexProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            Map<String, String> map = new HashMap<>();
            map.put("scalar1", "test secure1");
            map.put("scalar2", "test secure2");
            doc.setProperty(SECURED_SCHEMA, "complex", map);
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: complex since it is readonly", e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredComplexPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            Map<String, String> map = new HashMap<>();
            map.put("scalar1", "test secure1");
            map.put("scalar2", "test secure2");
            doc.setProperty(SECURED_SCHEMA, "complex", map);
            assertEquals("test secure1", doc.getProperty(SECURED_SCHEMA, "complex/scalar1"));
            assertEquals("test secure2", doc.getProperty(SECURED_SCHEMA, "complex/scalar2"));
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetUnsecuredComplexProperty() {
        DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
        // unsecureComplex/scalar1 is secured
        doc.setProperty(SECURED_SCHEMA, "unsecureComplex", singletonMap("scalar2", "test secure2"));
        assertEquals("test secure2", doc.getProperty(SECURED_SCHEMA, "unsecureComplex/scalar2"));
    }

    @Test
    public void testSetSecuredComplexItemProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "unsecureComplex", singletonMap("scalar1", "test secure1"));
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: unsecureComplex/scalar1 since it is readonly",
                         e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredComplexItemPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "unsecureComplex", singletonMap("scalar1", "test secure1"));
            assertEquals("test secure1", doc.getProperty(SECURED_SCHEMA, "unsecureComplex/scalar1"));
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredListProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "list",
                            asList(singletonMap("scalar1", "test secure1"), singletonMap("scalar2", "test secure2")));
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: list since it is readonly", e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredListPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "list",
                            asList(singletonMap("scalar1", "test secure1"), singletonMap("scalar2", "test secure2")));
            assertEquals("test secure1", doc.getProperty(SECURED_SCHEMA, "list/0/scalar1"));
            assertEquals("test secure2", doc.getProperty(SECURED_SCHEMA, "list/1/scalar2"));
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetUnsecuredListProperty() {
        DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
        // unsecureList/*/scalar1 is secured
        doc.setProperty(SECURED_SCHEMA, "unsecureList", singletonList(singletonMap("scalar2", "test secure2")));
        assertEquals("test secure2", doc.getProperty(SECURED_SCHEMA, "unsecureList/0/scalar2"));
    }

    @Test
    public void testSetSecuredListItemProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "unsecureList", singletonList(singletonMap("scalar1", "test secure1")));
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: unsecureList/-1/scalar1 since it is readonly",
                         e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredListItemPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "unsecureList", singletonList(singletonMap("scalar1", "test secure1")));
            assertEquals("test secure1", doc.getProperty(SECURED_SCHEMA, "unsecureList/0/scalar1"));
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredArrayProperty() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("john");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "array", asList("test secure1", "test secure2"));
            fail("A ReadOnlyPropertyException should have been thrown");
        } catch (ReadOnlyPropertyException e) {
            assertEquals("Cannot set the value of property: array since it is readonly", e.getMessage());
        } finally {
            ctx.logout();
        }
    }

    @Test
    public void testSetSecuredArrayPropertyWithAdministrator() throws LoginException {
        LoginContext ctx = Framework.loginAsUser("Administrator");
        try {
            DocumentModel doc = new DocumentModelImpl("/", "doc", "Secured");
            doc.setProperty(SECURED_SCHEMA, "array", asList("test secure1", "test secure2"));
            assertArrayEquals(new String[] { "test secure1", "test secure2" },
                              (String[]) doc.getProperty(SECURED_SCHEMA, "array"));
        } finally {
            ctx.logout();
        }
    }

}
