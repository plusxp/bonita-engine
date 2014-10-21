/*
 * Copyright (C) 2014 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.engine.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.commons.exceptions.SBonitaException;
import org.bonitasoft.engine.commons.exceptions.SObjectNotFoundException;
import org.bonitasoft.engine.core.document.api.DocumentService;
import org.bonitasoft.engine.core.document.api.impl.DocumentHelper;
import org.bonitasoft.engine.core.document.model.SMappedDocument;
import org.bonitasoft.engine.core.process.definition.ProcessDefinitionService;
import org.bonitasoft.engine.core.process.instance.api.ActivityInstanceService;
import org.bonitasoft.engine.core.process.instance.api.ProcessInstanceService;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SActivityInstanceNotFoundException;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SFlowNodeNotFoundException;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SFlowNodeReadException;
import org.bonitasoft.engine.data.instance.api.DataInstanceContainer;
import org.bonitasoft.engine.expression.exception.SExpressionDependencyMissingException;
import org.bonitasoft.engine.expression.exception.SExpressionEvaluationException;
import org.bonitasoft.engine.expression.model.ExpressionKind;
import org.bonitasoft.engine.expression.model.SExpression;
import org.bonitasoft.engine.persistence.QueryOptions;
import org.bonitasoft.engine.persistence.SBonitaReadException;
import org.bonitasoft.engine.service.ModelConvertor;

/**
 * @author Baptiste Mesta
 * @author Matthieu Chaffotte
 * @author Celine Souchet
 */
public class DocumentListReferenceExpressionExecutorStrategy extends NonEmptyContentExpressionExecutorStrategy {

    private final DocumentService documentService;

    private final ActivityInstanceService flowNodeInstanceService;

    private final DocumentHelper documentHelper;

    public DocumentListReferenceExpressionExecutorStrategy(final DocumentService documentService,
            final ActivityInstanceService flowNodeInstanceService, final ProcessDefinitionService processDefinitionService,
            final ProcessInstanceService processInstanceService) {
        this.documentService = documentService;
        this.flowNodeInstanceService = flowNodeInstanceService;
        documentHelper = new DocumentHelper(documentService, processDefinitionService, processInstanceService);
    }

    @Override
    public Object evaluate(final SExpression expression, final Map<String, Object> context, final Map<Integer, Object> resolvedExpressions,
            final ContainerState containerState) throws SExpressionEvaluationException, SExpressionDependencyMissingException {
        return evaluate(Collections.singletonList(expression), context, resolvedExpressions, containerState).get(0);
    }

    @Override
    public ExpressionKind getExpressionKind() {
        return KIND_DOCUMENT_LIST;
    }

    @Override
    public List<Object> evaluate(final List<SExpression> expressions, final Map<String, Object> context, final Map<Integer, Object> resolvedExpressions,
            final ContainerState containerState) throws SExpressionEvaluationException, SExpressionDependencyMissingException {
        final Long containerId = (Long) context.get(CONTAINER_ID_KEY);
        final String containerType = (String) context.get(CONTAINER_TYPE_KEY);

        try {
            final Long time = (Long) context.get("time");
            final long processInstanceId = getProcessInstance(containerId, containerType, time != null);
            final ArrayList<Object> results = new ArrayList<Object>(expressions.size());
            for (final SExpression expression : expressions) {
                results.add(getDocumentList(processInstanceId, expression.getContent(), time));
            }
            return results;
        } catch (final SExpressionDependencyMissingException e) {
            throw e;
        } catch (final SBonitaException e) {
            throw new SExpressionEvaluationException(e, null);
        }
    }

    List<Document> getDocumentList(final long processInstanceId, final String name, final Long time) throws SBonitaReadException {
        final List<SMappedDocument> documentList = getAllDocumentOfTheList(processInstanceId, name, time);
        try {
            if (documentList.isEmpty()
                    && !documentHelper.isListDefinedInDefinition(name, processInstanceId)) {
                return null;
            }
        } catch (final SObjectNotFoundException e) {
            return null;
        }
        return ModelConvertor.toDocuments(documentList, documentService);
    }

    private List<SMappedDocument> getAllDocumentOfTheList(final long processInstanceId, final String name, final Long time) throws SBonitaReadException {
        if (time != null) {
            return documentService.getDocumentList(name, processInstanceId, time);
        }
        QueryOptions queryOptions = new QueryOptions(0, 100);
        List<SMappedDocument> mappedDocuments;
        final List<SMappedDocument> result = new ArrayList<SMappedDocument>();
        do {
            mappedDocuments = documentService.getDocumentList(name, processInstanceId, queryOptions.getFromIndex(), queryOptions.getNumberOfResults());
            result.addAll(mappedDocuments);
            queryOptions = QueryOptions.getNextPage(queryOptions);
        } while (mappedDocuments.size() == 100);
        return result;
    }

    private long getProcessInstance(final Long containerId, final String containerType, final boolean flowNodeIsArchived) throws SFlowNodeNotFoundException,
            SFlowNodeReadException,
            SExpressionDependencyMissingException, SActivityInstanceNotFoundException {
        if (containerId == null || containerType == null) {
            throw new SExpressionDependencyMissingException("The context to retrieve the document is not set.");
        }
        if (DataInstanceContainer.PROCESS_INSTANCE.name().equals(containerType)) {
            return containerId;
        }
        if (flowNodeIsArchived) {
            return flowNodeInstanceService.getMostRecentArchivedActivityInstance(containerId).getParentProcessInstanceId();
        }
        return flowNodeInstanceService.getFlowNodeInstance(containerId).getParentProcessInstanceId();
    }

    @Override
    public boolean mustPutEvaluatedExpressionInContext() {
        return true;
    }

}
