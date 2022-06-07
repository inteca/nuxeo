/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     François Maturel
 */

package org.nuxeo.ecm.platform.ui.web.keycloak;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.tomcat.CatalinaHttpFacade;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;

/**
 *
 * @since 7.4
 */

public class DeploymentResult {
    private boolean isOk;

    private static KeycloakDeployment keycloakDeployment;

    private HttpServletRequest httpServletRequest;
    private HttpServletResponse httpServletResponse;
    private Request request;
    private CatalinaHttpFacade facade;

    public DeploymentResult(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
    }

    boolean isOk() {
        return isOk;
    }

    public static KeycloakDeployment getKeycloakDeployment() {
        return keycloakDeployment;
    }

    public Request getRequest() {
        return request;
    }

    public CatalinaHttpFacade getFacade() {
        return facade;
    }

    public DeploymentResult invokeOn(AdapterDeploymentContext deploymentContext) {

        // In Tomcat, a HttpServletRequest and a HttpServletResponse are wrapped in a Facades
        if (httpServletRequest instanceof RequestFacade) {
            // Received upon logout.
            request = unwrapRequest(httpServletRequest);
        } else {
            request = unwrapRequest(((ServletRequestWrapper) httpServletRequest).getRequest());
        }
        facade = new CatalinaHttpFacade(request, httpServletResponse);

        if (keycloakDeployment == null) {
            keycloakDeployment = deploymentContext.resolveDeployment(facade);
        }
        if (keycloakDeployment.isConfigured()) {
            isOk = true;
            return this;
        }
        isOk = false;
        return this;
    }

    /**
     * Get the wrapper {@link Request} hidden in a {@link ServletRequest} object
     *
     * @param servletRequest, the main ServletRequest object
     * @return the wrapper {@link Request} in {@link ServletRequest}
     */
    private Request unwrapRequest(final ServletRequest servletRequest) {
        try {
            final Field f = servletRequest.getClass().getDeclaredField("request");
            f.setAccessible(true); // grant access to (protected) field
            return (Request) f.get(servletRequest);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (final Exception e) {
            throw e;
        }
    }
}
