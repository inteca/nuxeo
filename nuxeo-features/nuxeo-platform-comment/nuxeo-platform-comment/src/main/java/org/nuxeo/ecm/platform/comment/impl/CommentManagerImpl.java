/*
 * (C) Copyright 2007-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.comment.impl;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.comment.api.CommentConstants;
import org.nuxeo.ecm.platform.comment.api.CommentConverter;
import org.nuxeo.ecm.platform.comment.api.CommentEvents;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.service.CommentServiceConfig;
import org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants;
import org.nuxeo.ecm.platform.relations.api.Graph;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.ResourceAdapter;
import org.nuxeo.ecm.platform.relations.api.Statement;
import org.nuxeo.ecm.platform.relations.api.impl.QNameResourceImpl;
import org.nuxeo.ecm.platform.relations.api.impl.ResourceImpl;
import org.nuxeo.ecm.platform.relations.api.impl.StatementImpl;
import org.nuxeo.ecm.platform.relations.jena.JenaGraph;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:glefter@nuxeo.com">George Lefter</a>
 */
public class CommentManagerImpl implements CommentManager {

    private static final Log log = LogFactory.getLog(CommentManagerImpl.class);

    final SimpleDateFormat timeFormat = new SimpleDateFormat("dd-HHmmss.S");

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM");

    final CommentServiceConfig config;

    final CommentConverter commentConverter;

    public static final String COMMENTS_DIRECTORY = "Comments";

    public CommentManagerImpl(CommentServiceConfig config) {
        this.config = config;
        commentConverter = config.getCommentConverter();
    }

    public List<DocumentModel> getComments(DocumentModel docModel) {
        return doPrivileged(docModel, session -> {
            Map<String, Object> ctxMap = Collections.singletonMap(ResourceAdapter.CORE_SESSION_CONTEXT_KEY, session);
            RelationManager relationManager = Framework.getService(RelationManager.class);
            Graph graph = relationManager.getGraph(config.graphName, session);
            Resource docResource = relationManager.getResource(config.documentNamespace, docModel, ctxMap);
            if (docResource == null) {
                throw new NuxeoException("Could not adapt document model to relation resource ; "
                        + "check the service relation adapters configuration");
            }

            // FIXME AT: why no filter on the predicate?
            List<Statement> statementList = graph.getStatements(null, null, docResource);
            if (graph instanceof JenaGraph) {
                // XXX AT: BBB for when repository name was not included in the
                // resource uri
                Resource oldDocResource = new QNameResourceImpl(config.documentNamespace, docModel.getId());
                statementList.addAll(graph.getStatements(null, null, oldDocResource));
            }

            List<DocumentModel> commentList = new ArrayList<DocumentModel>();
            for (Statement stmt : statementList) {
                QNameResourceImpl subject = (QNameResourceImpl) stmt.getSubject();

                DocumentModel commentDocModel = (DocumentModel) relationManager.getResourceRepresentation(
                        config.commentNamespace, subject, ctxMap);
                if (commentDocModel == null) {
                    // XXX AT: maybe user cannot see the comment
                    log.warn("Could not adapt comment relation subject to a document "
                            + "model; check the service relation adapters configur  ation");
                    continue;
                }
                // detach the document as it was loaded by a system session, not the user session.
                commentDocModel.detach(true);
                commentList.add(commentDocModel);
            }

            CommentSorter sorter = new CommentSorter(true);
            Collections.sort(commentList, sorter);
            return commentList;
        });
    }

    public DocumentModel createComment(DocumentModel docModel, String comment, String author) {
        try (CoreSession session = CoreInstance.openCoreSessionSystem(docModel.getRepositoryName())) {
            DocumentModel commentDM = session.createDocumentModel(CommentsConstants.COMMENT_DOC_TYPE);
            commentDM.setPropertyValue(CommentsConstants.COMMENT_TEXT, comment);
            commentDM.setPropertyValue(CommentsConstants.COMMENT_AUTHOR, author);
            commentDM.setPropertyValue(CommentsConstants.COMMENT_CREATION_DATE, Calendar.getInstance());
            commentDM = internalCreateComment(session, docModel, commentDM, null);
            session.save();

            return commentDM;
        }
    }

    public DocumentModel createComment(DocumentModel docModel, String comment) {
        String author = getCurrentUser(docModel);
        return createComment(docModel, comment, author);
    }

    /**
     * If the author property on comment is not set, retrieve the author name from the session
     *
     * @param docModel The document model that holds the session id
     * @param comment The comment to update
     */
    private static String updateAuthor(DocumentModel docModel, DocumentModel comment) {
        // update the author if not set
        String author = (String) comment.getProperty("comment", "author");
        if (author == null) {
            log.debug("deprecated use of createComment: the client should set the author property on document");
            author = getCurrentUser(docModel);
            comment.setProperty("comment", "author", author);
        }
        return author;
    }

    public DocumentModel createComment(DocumentModel docModel, DocumentModel comment) {
        try (CoreSession session = CoreInstance.openCoreSessionSystem(docModel.getRepositoryName())) {
            DocumentModel doc = internalCreateComment(session, docModel, comment, null);
            session.save();
            doc.detach(true);
            return doc;
        }
    }

    protected DocumentModel internalCreateComment(CoreSession session, DocumentModel docModel, DocumentModel comment,
            String path) {
        String author = updateAuthor(docModel, comment);
        DocumentModel createdComment;

        createdComment = createCommentDocModel(session, docModel, comment, path);

        RelationManager relationManager = Framework.getService(RelationManager.class);

        Resource commentRes = relationManager.getResource(config.commentNamespace, createdComment, null);

        Resource documentRes = relationManager.getResource(config.documentNamespace, docModel, null);

        if (commentRes == null || documentRes == null) {
            throw new NuxeoException("Could not adapt document model to relation resource ; "
                    + "check the service relation adapters configuration");
        }

        Resource predicateRes = new ResourceImpl(config.predicateNamespace);

        Statement stmt = new StatementImpl(commentRes, predicateRes, documentRes);
        relationManager.getGraph(config.graphName, session).add(stmt);

        UserManager userManager = Framework.getService(UserManager.class);
        if (userManager != null) {
            // null in tests
            NuxeoPrincipal principal = userManager.getPrincipal(author);
            if (principal != null) {
                notifyEvent(session, docModel, CommentEvents.COMMENT_ADDED, null, createdComment, principal);
            }
        }

        return createdComment;
    }

    private DocumentModel createCommentDocModel(CoreSession mySession, DocumentModel docModel, DocumentModel comment,
            String path) {

        String domainPath;
        updateAuthor(docModel, comment);

        String[] pathList = getCommentPathList(comment);

        if (path == null) {
            domainPath = "/" + docModel.getPath().segment(0);
        } else {
            domainPath = path;
        }
        if (mySession == null) {
            return null;
        }

        // TODO GR upgrade this code. It can't work if current user
        // doesn't have admin rights

        DocumentModel parent = mySession.getDocument(new PathRef(domainPath));
        for (String name : pathList) {
            String pathStr = parent.getPathAsString();

            PathRef ref = new PathRef(pathStr, name);
            if (mySession.exists(ref)) {
                parent = mySession.getDocument(ref);
                if (!parent.isFolder()) {
                    throw new NuxeoException(parent.getPathAsString() + " is not folderish");
                }
            } else {
                parent = createHiddenFolder(mySession, pathStr, name);
            }
        }

        String pathStr = parent.getPathAsString();
        String commentName = getCommentName(docModel, comment);
        CommentConverter converter = config.getCommentConverter();
        PathSegmentService pss = Framework.getService(PathSegmentService.class);
        DocumentModel commentDocModel = mySession.createDocumentModel(comment.getType());
        commentDocModel.setProperty("dublincore", "title", commentName);
        converter.updateDocumentModel(commentDocModel, comment);
        commentDocModel.setPathInfo(pathStr, pss.generatePathSegment(commentDocModel));
        commentDocModel = mySession.createDocument(commentDocModel);
        log.debug("created comment with id=" + commentDocModel.getId());

        return commentDocModel;
    }

    protected DocumentModel createHiddenFolder(CoreSession session, String parentPath, String name) {
        DocumentModel dm = session.createDocumentModel(parentPath, name, "HiddenFolder");
        dm.setProperty("dublincore", "title", name);
        dm.setProperty("dublincore", "description", "");
        Framework.doPrivileged(() -> dm.setProperty("dublincore", "created", Calendar.getInstance()));
        DocumentModel parent = session.createDocument(dm); // change variable name to be effectively final
        setFolderPermissions(parent);
        return parent;
    }

    private static void notifyEvent(CoreSession session, DocumentModel docModel, String eventType,
            DocumentModel parent, DocumentModel child, NuxeoPrincipal principal) {

        DocumentEventContext ctx = new DocumentEventContext(session, principal, docModel);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        if (parent != null) {
            props.put(CommentConstants.PARENT_COMMENT, parent);
        }
        props.put(CommentConstants.COMMENT_DOCUMENT, child);
        props.put(CommentConstants.COMMENT, (String) child.getProperty("comment", "text"));
        // Keep comment_text for compatibility
        props.put(CommentConstants.COMMENT_TEXT, (String) child.getProperty("comment", "text"));
        props.put("category", CommentConstants.EVENT_COMMENT_CATEGORY);
        ctx.setProperties(props);
        Event event = ctx.newEvent(eventType);

        EventProducer evtProducer = Framework.getService(EventProducer.class);
        evtProducer.fireEvent(event);
        // send also a synchronous Seam message so the CommentManagerActionBean
        // can rebuild its list
        // Events.instance().raiseEvent(eventType, docModel);
    }

    private static void setFolderPermissions(DocumentModel dm) {
        ACP acp = dm.getACP();
        acp.blockInheritance(ACL.LOCAL_ACL, SecurityConstants.SYSTEM_USERNAME);
        dm.setACP(acp, true);
    }

    private String[] getCommentPathList(DocumentModel comment) {
        String[] pathList = new String[2];
        pathList[0] = COMMENTS_DIRECTORY;

        pathList[1] = dateFormat.format(getCommentTimeStamp(comment));
        return pathList;
    }

    /**
     * @deprecated if the caller is remote, we cannot obtain the session
     */
    @Deprecated
    private static String getCurrentUser(DocumentModel target) {
        CoreSession userSession = target.getCoreSession();
        if (userSession == null) {
            throw new NuxeoException("userSession is null, do not invoke this method when the user is not local");
        }
        return userSession.getPrincipal().getName();
    }

    private String getCommentName(DocumentModel target, DocumentModel comment) {
        String author = (String) comment.getProperty("comment", "author");
        if (author == null) {
            author = getCurrentUser(target);
        }
        Date creationDate = getCommentTimeStamp(comment);
        return "COMMENT-" + author + '-' + timeFormat.format(creationDate.getTime());
    }

    private static Date getCommentTimeStamp(DocumentModel comment) {
        Calendar creationDate;
        try {
            creationDate = (Calendar) comment.getProperty("dublincore", "created");
        } catch (PropertyException e) {
            creationDate = null;
        }
        if (creationDate == null) {
            creationDate = Calendar.getInstance();
        }
        return creationDate.getTime();
    }

    public void deleteComment(DocumentModel docModel, DocumentModel comment) {
        NuxeoPrincipal author = comment.getCoreSession() != null ? (NuxeoPrincipal) comment.getCoreSession().getPrincipal()
                : getAuthor(comment);
        try (CoreSession session = CoreInstance.openCoreSessionSystem(docModel.getRepositoryName())) {
            DocumentRef ref = comment.getRef();
            if (!session.exists(ref)) {
                throw new NuxeoException("Comment Document does not exist: " + comment.getId());
            }

            session.removeDocument(ref);

            notifyEvent(session, docModel, CommentEvents.COMMENT_REMOVED, null, comment, author);

            session.save();
        }
    }

    public DocumentModel createComment(DocumentModel docModel, DocumentModel parent, DocumentModel child)
            {
        try (CoreSession session = CoreInstance.openCoreSessionSystem(docModel.getRepositoryName())) {
            DocumentModel parentDocModel = session.getDocument(parent.getRef());
            DocumentModel newComment = internalCreateComment(session, parentDocModel, child, null);

            session.save();
            return newComment;
        }
    }

    private static NuxeoPrincipal getAuthor(DocumentModel docModel) {
        String[] contributors;
        try {
            contributors = (String[]) docModel.getProperty("dublincore", "contributors");
        } catch (PropertyException e) {
            log.error("Error building principal for comment author", e);
            return null;
        }
        UserManager userManager = Framework.getService(UserManager.class);
        return userManager.getPrincipal(contributors[0]);
    }

    public List<DocumentModel> getComments(DocumentModel docModel, DocumentModel parent) {
        return getComments(parent);
    }

    public List<DocumentModel> getDocumentsForComment(DocumentModel comment) {
        return doPrivileged(comment, session -> {
            Map<String, Object> ctxMap = Collections.singletonMap(ResourceAdapter.CORE_SESSION_CONTEXT_KEY, session);
            RelationManager relationManager = Framework.getService(RelationManager.class);
            Graph graph = relationManager.getGraph(config.graphName, session);
            Resource commentResource = relationManager.getResource(config.commentNamespace, comment, ctxMap);
            if (commentResource == null) {
                throw new NuxeoException("Could not adapt document model to relation resource ; "
                        + "check the service relation adapters configuration");
            }
            Resource predicate = new ResourceImpl(config.predicateNamespace);

            List<Statement> statementList = graph.getStatements(commentResource, predicate, null);
            if (graph instanceof JenaGraph) {
                // XXX AT: BBB for when repository name was not included in the
                // resource uri
                Resource oldDocResource = new QNameResourceImpl(config.commentNamespace, comment.getId());
                statementList.addAll(graph.getStatements(oldDocResource, predicate, null));
            }

            List<DocumentModel> docList = new ArrayList<DocumentModel>();
            for (Statement stmt : statementList) {
                QNameResourceImpl subject = (QNameResourceImpl) stmt.getObject();
                DocumentModel docModel = (DocumentModel) relationManager.getResourceRepresentation(
                        config.documentNamespace, subject, ctxMap);
                if (docModel == null) {
                    log.warn("Could not adapt comment relation subject to a document "
                            + "model; check the service relation adapters configuration");
                    continue;
                }
                // detach the document as it was loaded by a system session, not the user session.
                docModel.detach(true);
                docList.add(docModel);
            }
            return docList;
        });
    }

    /**
     * @since 11.1
     */
    protected List<DocumentModel> doPrivileged(DocumentModel doc, Function<CoreSession, List<DocumentModel>> function) {
        if (doc.getCoreSession() == null) {
            return CoreInstance.doPrivileged(doc.getRepositoryName(), function);
        }
        return CoreInstance.doPrivileged(doc.getCoreSession(), function);
    }

    public DocumentModel createLocatedComment(DocumentModel docModel, DocumentModel comment, String path)
            {
        try (CoreSession session = CoreInstance.openCoreSessionSystem(docModel.getRepositoryName())) {
            DocumentModel createdComment = internalCreateComment(session, docModel, comment, path);
            session.save();
            return createdComment;
        }
    }

    public DocumentModel getThreadForComment(DocumentModel comment) {
        List<DocumentModel> threads = getDocumentsForComment(comment);
        if (threads.size() > 0) {
            DocumentModel thread = (DocumentModel) threads.get(0);
            while (thread.getType().equals("Post") || thread.getType().equals(CommentsConstants.COMMENT_DOC_TYPE)) {
                thread = getThreadForComment(thread);
            }
            return thread;
        }
        return null;
    }
}
