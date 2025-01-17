/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.drive.service.impl;

import java.io.IOException;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.impl.SimpleFileSystemItem;

/**
 * {@link JsonDeserializer} for a {@link FileSystemItem}.
 *
 * @since 9.10-HF01, 10.1
 */
public class FileSystemItemDeserializer extends JsonDeserializer<FileSystemItem> {

    @Override
    public FileSystemItem deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
        return jp.readValueAs(SimpleFileSystemItem.class);
    }

}
