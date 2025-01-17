/*
 * (C) Copyright 2018 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.core;

import java.security.Principal;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.local.ClientLoginModule;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.api.login.LoginAs;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Dummy {@link LoginAs} implementation which logs the given user into the application.
 *
 * @since 11.1
 */
public class DummyLoginAs extends DefaultComponent implements LoginAs {

    @Override
    @SuppressWarnings("deprecation")
    public LoginContext loginAs(String username) throws LoginException {
        boolean isAdministrator = SecurityConstants.ADMINISTRATOR.equals(username);
        Principal principal = new UserPrincipal(username, null, false, isAdministrator);
        ClientLoginModule.getThreadLocalLogin().push(principal, null, null);
        return new LoginContext("nuxeo-client-login") {
            @Override
            public void logout() throws LoginException {
                ClientLoginModule.getThreadLocalLogin().pop();
            }
        };
    }
};
