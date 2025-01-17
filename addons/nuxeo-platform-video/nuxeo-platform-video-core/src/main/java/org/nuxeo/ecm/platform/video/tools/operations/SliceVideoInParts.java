/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thibaud Arguillere
 *     Ricardo Dias
 */
package org.nuxeo.ecm.platform.video.tools.operations;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.video.tools.VideoToolsService;
import org.nuxeo.runtime.api.Framework;

/**
 * Operation for slicing a video in parts with approximately equal duration.
 *
 * @since 8.4
 */
@Operation(id = SliceVideoInParts.ID, category = Constants.CAT_CONVERSION, label = "SliceVideo a Video in Parts with equal duration.", description = "Slices the video in n parts of approximately the same duration each.", aliases = {
        "Video.SliceInParts" })
public class SliceVideoInParts {

    public static final String ID = "Video.SliceInParts";

    @Param(name = "duration", required = false)
    protected String duration;

    @Param(name = "xpath", required = false)
    protected String xpath;

    @OperationMethod
    public BlobList run(DocumentModel input) throws OperationException {
        if (StringUtils.isEmpty(xpath)) {
            return run(input.getAdapter(BlobHolder.class).getBlob());
        } else {
            return run((Blob) input.getPropertyValue(xpath));
        }
    }

    @OperationMethod
    public BlobList run(Blob input) throws OperationException {
        try {
            VideoToolsService service = Framework.getService(VideoToolsService.class);
            return new BlobList(service.slice(input, "", duration, false));
        } catch(NuxeoException e){
            throw new OperationException(e);
        }
    }
}
