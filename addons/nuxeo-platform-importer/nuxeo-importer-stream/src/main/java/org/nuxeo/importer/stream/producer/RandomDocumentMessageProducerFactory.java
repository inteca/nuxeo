/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.importer.stream.producer;

import java.util.Collections;

import org.nuxeo.importer.stream.message.BlobInfoMessage;
import org.nuxeo.importer.stream.message.DocumentMessage;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogPartition;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.lib.stream.pattern.producer.ProducerFactory;
import org.nuxeo.lib.stream.pattern.producer.ProducerIterator;

/**
 * @since 9.1
 */
public class RandomDocumentMessageProducerFactory implements ProducerFactory<DocumentMessage> {
    protected final long nbDocuments;

    protected final String lang;

    protected final int blobSizeKb;

    protected final LogManager manager;

    protected final String logName;

    /**
     * Generates random document messages that contains random blob.
     */
    public RandomDocumentMessageProducerFactory(long nbDocuments, String lang, int blobSizeKb) {
        this.nbDocuments = nbDocuments;
        this.lang = lang;
        this.manager = null;
        this.blobSizeKb = blobSizeKb;
        this.logName = null;
    }

    /**
     * Generates random documents messages that point to existing blobs.
     */
    public RandomDocumentMessageProducerFactory(long nbDocuments, String lang, LogManager manager,
            String logBlobInfoName) {
        this.nbDocuments = nbDocuments;
        this.lang = lang;
        this.manager = manager;
        this.logName = logBlobInfoName;
        this.blobSizeKb = 0;
    }

    @SuppressWarnings("resource")
    @Override
    public ProducerIterator<DocumentMessage> createProducer(int producerId) {
        BlobInfoFetcher fetcher = null;
        if (manager != null) {
            LogTailer<BlobInfoMessage> tailer = manager.createTailer(getGroupName(producerId),
                    Collections.singleton(LogPartition.of(logName, 0)));
            fetcher = new RandomLogBlobInfoFetcher(tailer);
        }
        return new RandomDocumentMessageProducer(producerId, nbDocuments, lang, fetcher).withBlob(blobSizeKb, false);
    }

    protected String getGroupName(int producerId) {
        return "RandomDocumentMessageProducer." + producerId;
    }

}
