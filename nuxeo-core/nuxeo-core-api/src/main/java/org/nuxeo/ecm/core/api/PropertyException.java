/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.api;

/**
 * Base exception thrown when there is a problem accessing a property.
 */
//inherits from a deprecated base PropertyException so that old code catching the old one still works
@SuppressWarnings("deprecation")
public class PropertyException extends org.nuxeo.ecm.core.api.model.PropertyException {

    private static final long serialVersionUID = 1L;

    public PropertyException() {
        super();
    }

    /**
     * @since 11.1
     */
    public PropertyException(int statusCode) {
        super(statusCode);
    }

    public PropertyException(String message) {
        super(message);
    }

    /**
     * @since 11.1
     */
    public PropertyException(String message, int statusCode) {
        super(message, statusCode);
    }

    public PropertyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @since 11.1
     */
    public PropertyException(String message, Throwable cause, int statusCode) {
        super(message, cause, statusCode);
    }

    public PropertyException(Throwable cause) {
        super(cause);
    }

    /**
     * @since 11.
     */
    public PropertyException(Throwable cause, int statusCode) {
        super(cause, statusCode);
    }
}
