/*
 * (C) Copyright 2019 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.runtime.stream.tests;

import java.util.Map;

import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.RecordFilter;

/**
 * Change the record key that match a pattern.
 *
 * @since 11.1
 */
public class ChangeFilter implements RecordFilter {
    protected String match;

    protected String replace;

    @Override
    public void init(Map<String, String> options) {
        match = options.get("keyMatch");
        replace = options.get("replace");
    }

    @Override
    public Record beforeAppend(Record record) {
        if (record.key.contains(match)) {
            return Record.of(record.key.replace(match, replace), record.data);
        }
        return record;
    }
}
