/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.test;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.jaxrs.test.JerseyClientHelper;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.MultiPartMediaTypes;

/**
 * @since 5.7.2
 */
public class BaseTest {

    protected static final String REST_API_URL = "http://localhost:18090/api/v1/";

    protected static enum RequestType {
        GET, POST, DELETE, PUT, POSTREQUEST, GETES
    }

    protected ObjectMapper mapper;

    protected Client client;

    protected WebResource service;

    @Before
    public void doBefore() throws Exception {
        service = getServiceFor("Administrator", "Administrator");
        mapper = new ObjectMapper();
    }

    @After
    public void doAfter() throws Exception {
        client.destroy();
    }

    /**
     * Returns a {@link WebResource} to perform REST API calls with the given credentials.
     * <p>
     * Since 9.3, uses the Apache HTTP client, more reliable and much more configurable than the one from the JDK.
     *
     * @since 5.7.3
     */
    protected WebResource getServiceFor(String username, String password) {
        return getServiceFor(REST_API_URL, username, password);
    }

    /**
     * Returns a {@link WebResource} to perform calls on the given resource with the given credentials.
     * <p>
     * Uses the Apache HTTP client, more reliable and much more configurable than the one from the JDK.
     *
     * @since 9.3
     */
    protected WebResource getServiceFor(String resource, String username, String password) {
        if (client != null) {
            client.destroy();
        }
        client = JerseyClientHelper.clientBuilder().setCredentials(username, password).build();
        return client.resource(resource);
    }

    @Inject
    public CoreSession session;

    protected CloseableClientResponse getResponse(RequestType requestType, String path) {
        return getResponse(requestType, path, null, null, null, null);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, Map<String, String> headers) {
        return getResponse(requestType, path, null, null, null, headers);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, MultiPart mp) {
        return getResponse(requestType, path, null, null, mp, null);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, MultiPart mp,
            Map<String, String> headers) {
        return getResponse(requestType, path, null, null, mp, headers);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path,
            MultivaluedMap<String, String> queryParams) {
        return getResponse(requestType, path, null, queryParams, null, null);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, String data) {
        return getResponse(requestType, path, data, null, null, null);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, String data,
            Map<String, String> headers) {
        return getResponse(requestType, path, data, null, null, headers);
    }

    protected CloseableClientResponse getResponse(RequestType requestType, String path, String data,
            MultivaluedMap<String, String> queryParams, MultiPart mp, Map<String, String> headers) {

        WebResource wr = service.path(path);

        if (queryParams != null && !queryParams.isEmpty()) {
            wr = wr.queryParams(queryParams);
        }
        Builder builder;
        if (requestType == RequestType.GETES) {
            builder = wr.accept("application/json+esentity");
        } else {
            builder = wr.accept(MediaType.APPLICATION_JSON).header("X-NXDocumentProperties", "dublincore");
        }

        // Adding some headers if needed
        if (headers != null && !headers.isEmpty()) {
            for (String headerKey : headers.keySet()) {
                builder.header(headerKey, headers.get(headerKey));
            }
        }
        ClientResponse response = null;
        switch (requestType) {
        case GET:
        case GETES:
            response = builder.get(ClientResponse.class);
            break;
        case POST:
        case POSTREQUEST:
            if (mp != null) {
                response = builder.type(MultiPartMediaTypes.createFormData()).post(ClientResponse.class, mp);
            } else if (data != null) {
                setJSONContentTypeIfAbsent(builder, headers);
                response = builder.post(ClientResponse.class, data);
            } else {
                response = builder.post(ClientResponse.class);
            }
            break;
        case PUT:
            if (mp != null) {
                response = builder.type(MultiPartMediaTypes.createFormData()).put(ClientResponse.class, mp);
            } else if (data != null) {
                setJSONContentTypeIfAbsent(builder, headers);
                response = builder.put(ClientResponse.class, data);
            } else {
                response = builder.put(ClientResponse.class);
            }
            break;
        case DELETE:
            response = builder.delete(ClientResponse.class, data);
            break;
        default:
            throw new RuntimeException();
        }

        // Make the ClientResponse AutoCloseable by wrapping it in a CloseableClientResponse.
        // This is to strongly encourage the caller to use a try-with-resources block to make sure the response is
        // closed and avoid leaking connections.
        return CloseableClientResponse.of(response);
    }

    /** @since 9.3 */
    protected void setJSONContentTypeIfAbsent(Builder builder, Map<String, String> headers) {
        if (headers == null || !(headers.containsKey("Content-Type"))) {
            builder.type(MediaType.APPLICATION_JSON);
        }
    }

    protected JsonNode getResponseAsJson(RequestType responseType, String url)
            throws IOException, JsonProcessingException {
        return getResponseAsJson(responseType, url, null);
    }

    /**
     * @param get
     * @param string
     * @param queryParamsForPage
     * @return
     * @throws IOException
     * @throws JsonProcessingException
     * @since 5.8
     */
    protected JsonNode getResponseAsJson(RequestType responseType, String url,
            MultivaluedMap<String, String> queryParams) throws JsonProcessingException, IOException {
        try (CloseableClientResponse response = getResponse(responseType, url, queryParams)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            return mapper.readTree(response.getEntityInputStream());
        }
    }

    /**
     * Fetch session invalidations.
     *
     * @since 5.9.3
     */
    protected void fetchInvalidations() {
        session.save();
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    protected void assertNodeEqualsDoc(JsonNode node, DocumentModel note) throws Exception {
        assertEquals("document", node.get("entity-type").getValueAsText());
        assertEquals(note.getPathAsString(), node.get("path").getValueAsText());
        assertEquals(note.getId(), node.get("uid").getValueAsText());
        assertEquals(note.getTitle(), node.get("title").getValueAsText());
    }

    protected List<JsonNode> getLogEntries(JsonNode node) {
        assertEquals("documents", node.get("entity-type").getValueAsText());
        assertTrue(node.get("entries").isArray());
        List<JsonNode> result = new ArrayList<>();
        Iterator<JsonNode> elements = node.get("entries").getElements();
        while (elements.hasNext()) {
            result.add(elements.next());
        }
        return result;
    }

    /**
     * @since 7.1
     */
    protected String getErrorMessage(JsonNode node) {
        assertEquals("exception", node.get("entity-type").getValueAsText());
        assertTrue(node.get("message").isTextual());
        return node.get("message").getValueAsText();
    }

    protected void assertEntityEqualsDoc(InputStream in, DocumentModel doc) throws Exception {

        JsonNode node = mapper.readTree(in);
        assertNodeEqualsDoc(node, doc);

    }

    /**
     * Builds and returns a {@link MultivaluedMap} filled with the given simple values.
     *
     * @since 11.1
     */
    protected MultivaluedMap<String, String> multiOf(String k1, String v1) {
        return multiOf(singletonMap(k1, v1));
    }

    /**
     * Builds and returns a {@link MultivaluedMap} filled with the given simple values.
     *
     * @since 11.1
     */
    protected MultivaluedMap<String, String> multiOf(String k1, String v1, String k2, String v2) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        if (k1 != null) {
            queryParams.putSingle(k1, v1);
        }
        if (k2 != null) {
            queryParams.putSingle(k2, v2);
        }
        return queryParams;
    }

    /**
     * Builds and returns a {@link MultivaluedMap} filled with the given simple values.
     *
     * @since 11.1
     */
    protected MultivaluedMap<String, String> multiOf(Map<String, String> map) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        map.forEach(queryParams::putSingle);
        return queryParams;
    }
}
