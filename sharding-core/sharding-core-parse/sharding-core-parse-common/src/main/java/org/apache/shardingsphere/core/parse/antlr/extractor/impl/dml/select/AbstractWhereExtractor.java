/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.core.parse.antlr.extractor.impl.dml.select;

import com.google.common.base.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.shardingsphere.core.parse.antlr.extractor.api.OptionalSQLSegmentExtractor;
import org.apache.shardingsphere.core.parse.antlr.extractor.impl.dml.PredicateExtractor;
import org.apache.shardingsphere.core.parse.antlr.extractor.util.ExtractorUtils;
import org.apache.shardingsphere.core.parse.antlr.extractor.util.RuleName;
import org.apache.shardingsphere.core.parse.antlr.sql.segment.dml.WhereSegment;
import org.apache.shardingsphere.core.parse.antlr.sql.segment.dml.condition.OrConditionSegment;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract where extractor.
 *
 * @author duhongjun
 */
public abstract class AbstractWhereExtractor implements OptionalSQLSegmentExtractor {
    
    private final PredicateExtractor predicateSegmentExtractor = new PredicateExtractor();
    
    @Override
    public Optional<WhereSegment> extract(final ParserRuleContext ancestorNode) {
        return extract(ancestorNode, ancestorNode);
    }
    
    /**
     * Extract SQL segment from SQL AST.
     *
     * @param ancestorNode ancestor node of AST
     * @param rootNode root node of AST
     * @return SQL segment
     */
    public Optional<WhereSegment> extract(final ParserRuleContext ancestorNode, final ParserRuleContext rootNode) {
        WhereSegment result = new WhereSegment();
        Map<ParserRuleContext, Integer> placeholderIndexes = getPlaceholderIndexes(rootNode);
        result.setParameterCount(placeholderIndexes.size());
        Optional<ParserRuleContext> whereNode = extractWhere(ancestorNode);
        if (whereNode.isPresent()) {
            result.setWhereStartIndex(whereNode.get().getStart().getStartIndex());
            result.setWhereStopIndex(whereNode.get().getStop().getStopIndex());
            if (!placeholderIndexes.isEmpty()) {
                Collection<ParserRuleContext> questionNodes = ExtractorUtils.getAllDescendantNodes(whereNode.get(), RuleName.QUESTION);
                if (!questionNodes.isEmpty()) {
                    int index = placeholderIndexes.get(questionNodes.iterator().next());
                    result.setWhereParameterStartIndex(index);
                    result.setWhereParameterEndIndex(index + questionNodes.size() - 1);
                }
            }
            extractAndFillWhere(result, placeholderIndexes, whereNode.get());
        }
        return Optional.of(result);
    }
    
    private Map<ParserRuleContext, Integer> getPlaceholderIndexes(final ParserRuleContext rootNode) {
        Collection<ParserRuleContext> placeholderNodes = ExtractorUtils.getAllDescendantNodes(rootNode, RuleName.QUESTION);
        Map<ParserRuleContext, Integer> result = new HashMap<>(placeholderNodes.size(), 1);
        int index = 0;
        for (ParserRuleContext each : placeholderNodes) {
            result.put(each, index++);
        }
        return result;
    }
    
    protected abstract Optional<ParserRuleContext> extractWhere(ParserRuleContext ancestorNode);
    
    private void extractAndFillWhere(final WhereSegment whereSegment, final Map<ParserRuleContext, Integer> placeholderIndexes, final ParserRuleContext whereNode) {
        Optional<OrConditionSegment> conditions = buildCondition((ParserRuleContext) whereNode.getChild(1), placeholderIndexes);
        if (conditions.isPresent()) {
            whereSegment.getConditions().getAndConditions().addAll(conditions.get().getAndConditions());
        }
    }
    
    private Optional<OrConditionSegment> buildCondition(final ParserRuleContext node, final Map<ParserRuleContext, Integer> placeholderIndexes) {
        Optional<ParserRuleContext> exprNode = ExtractorUtils.findFirstChildNode(node, RuleName.EXPR);
        return exprNode.isPresent() ? predicateSegmentExtractor.extract(placeholderIndexes, exprNode.get()) : Optional.<OrConditionSegment>absent();
    }
}
