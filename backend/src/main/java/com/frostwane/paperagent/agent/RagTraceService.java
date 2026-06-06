package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagTraceService {

    private final RagTraceRepository ragTraceRepository;
    private final EntityManager entityManager;

    public RagTraceService(RagTraceRepository ragTraceRepository, EntityManager entityManager) {
        this.ragTraceRepository = ragTraceRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void recordSuccess(
        User owner,
        Paper paper,
        ChatRecord chatRecord,
        String scope,
        String question,
        String modelName,
        String pipelineName,
        String nodeSpansJson,
        String retrievalChannelsJson,
        String retrievalProcessorsJson,
        String queryExpansionsJson,
        String queryIntent,
        String searchQuery,
        boolean queryRewriteEnabled,
        String rewrittenQuery,
        String querySubQuestionsJson,
        String queryRewriteModelName,
        boolean comparisonRequested,
        String answerStrategy,
        String answerContract,
        int sourceCount,
        int memoryTurnCount,
        int memoryChars,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int evaluationMs,
        int answerQualityScore,
        String answerQualityLabel,
        String answerQualityNotes,
        String answerQualityMethod,
        boolean answerQualityJudgeEnabled,
        String answerQualityJudgeModelName,
        int answerQualityConfidence,
        int totalMs
    ) {
        ragTraceRepository.save(trace(
            owner,
            paper,
            chatRecord,
            scope,
            question,
            "SUCCESS",
            modelName,
            pipelineName,
            nodeSpansJson,
            retrievalChannelsJson,
            retrievalProcessorsJson,
            queryExpansionsJson,
            queryIntent,
            searchQuery,
            queryRewriteEnabled,
            rewrittenQuery,
            querySubQuestionsJson,
            queryRewriteModelName,
            comparisonRequested,
            answerStrategy,
            answerContract,
            sourceCount,
            memoryTurnCount,
            memoryChars,
            retrievalMs,
            generationMs,
            verificationMs,
            formattingMs,
            evaluationMs,
            answerQualityScore,
            answerQualityLabel,
            answerQualityNotes,
            answerQualityMethod,
            answerQualityJudgeEnabled,
            answerQualityJudgeModelName,
            answerQualityConfidence,
            totalMs,
            null
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
        User owner,
        Paper paper,
        String scope,
        String question,
        String modelName,
        String pipelineName,
        String nodeSpansJson,
        String retrievalChannelsJson,
        String retrievalProcessorsJson,
        String queryExpansionsJson,
        String queryIntent,
        String searchQuery,
        boolean queryRewriteEnabled,
        String rewrittenQuery,
        String querySubQuestionsJson,
        String queryRewriteModelName,
        boolean comparisonRequested,
        String answerStrategy,
        String answerContract,
        int sourceCount,
        int memoryTurnCount,
        int memoryChars,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int evaluationMs,
        int answerQualityScore,
        String answerQualityLabel,
        String answerQualityNotes,
        String answerQualityMethod,
        boolean answerQualityJudgeEnabled,
        String answerQualityJudgeModelName,
        int answerQualityConfidence,
        int totalMs,
        String errorMessage
    ) {
        ragTraceRepository.save(trace(
            owner,
            paper,
            null,
            scope,
            question,
            "FAILED",
            modelName,
            pipelineName,
            nodeSpansJson,
            retrievalChannelsJson,
            retrievalProcessorsJson,
            queryExpansionsJson,
            queryIntent,
            searchQuery,
            queryRewriteEnabled,
            rewrittenQuery,
            querySubQuestionsJson,
            queryRewriteModelName,
            comparisonRequested,
            answerStrategy,
            answerContract,
            sourceCount,
            memoryTurnCount,
            memoryChars,
            retrievalMs,
            generationMs,
            verificationMs,
            formattingMs,
            evaluationMs,
            answerQualityScore,
            answerQualityLabel,
            answerQualityNotes,
            answerQualityMethod,
            answerQualityJudgeEnabled,
            answerQualityJudgeModelName,
            answerQualityConfidence,
            totalMs,
            sanitizeError(errorMessage)
        ));
    }

    private RagTrace trace(
        User owner,
        Paper paper,
        ChatRecord chatRecord,
        String scope,
        String question,
        String status,
        String modelName,
        String pipelineName,
        String nodeSpansJson,
        String retrievalChannelsJson,
        String retrievalProcessorsJson,
        String queryExpansionsJson,
        String queryIntent,
        String searchQuery,
        boolean queryRewriteEnabled,
        String rewrittenQuery,
        String querySubQuestionsJson,
        String queryRewriteModelName,
        boolean comparisonRequested,
        String answerStrategy,
        String answerContract,
        int sourceCount,
        int memoryTurnCount,
        int memoryChars,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
        int evaluationMs,
        int answerQualityScore,
        String answerQualityLabel,
        String answerQualityNotes,
        String answerQualityMethod,
        boolean answerQualityJudgeEnabled,
        String answerQualityJudgeModelName,
        int answerQualityConfidence,
        int totalMs,
        String errorMessage
    ) {
        RagTrace trace = new RagTrace();
        trace.setOwner(entityManager.getReference(User.class, owner.getId()));
        trace.setPaper(paper == null ? null : entityManager.getReference(Paper.class, paper.getId()));
        trace.setChatRecord(chatRecord == null ? null : entityManager.getReference(ChatRecord.class, chatRecord.getId()));
        trace.setScope(scope);
        trace.setQuestion(question);
        trace.setStatus(status);
        trace.setModelName(modelName);
        trace.setPipelineName(defaultText(pipelineName, "agent-pipeline-v1"));
        trace.setNodeSpansJson(defaultText(nodeSpansJson, "[]"));
        trace.setRetrievalChannelsJson(defaultText(retrievalChannelsJson, "[]"));
        trace.setRetrievalProcessorsJson(defaultText(retrievalProcessorsJson, "[]"));
        trace.setQueryExpansionsJson(defaultText(queryExpansionsJson, "[]"));
        trace.setQueryIntent(defaultText(queryIntent, "GENERAL_QA"));
        trace.setSearchQuery(searchQuery);
        trace.setQueryRewriteEnabled(queryRewriteEnabled);
        trace.setRewrittenQuery(rewrittenQuery);
        trace.setQuerySubQuestionsJson(defaultText(querySubQuestionsJson, "[]"));
        trace.setQueryRewriteModelName(queryRewriteModelName);
        trace.setComparisonRequested(comparisonRequested);
        trace.setAnswerStrategy(defaultText(answerStrategy, "EVIDENCE_GROUNDED_QA"));
        trace.setAnswerContract(answerContract);
        trace.setSourceCount(sourceCount);
        trace.setMemoryTurnCount(Math.max(0, memoryTurnCount));
        trace.setMemoryChars(Math.max(0, memoryChars));
        trace.setRetrievalMs(retrievalMs);
        trace.setGenerationMs(generationMs);
        trace.setVerificationMs(verificationMs);
        trace.setFormattingMs(formattingMs);
        trace.setEvaluationMs(evaluationMs);
        trace.setAnswerQualityScore(Math.max(0, Math.min(100, answerQualityScore)));
        trace.setAnswerQualityLabel(defaultText(answerQualityLabel, "UNASSESSED"));
        trace.setAnswerQualityNotes(answerQualityNotes);
        trace.setAnswerQualityMethod(defaultText(answerQualityMethod, "HEURISTIC"));
        trace.setAnswerQualityJudgeEnabled(answerQualityJudgeEnabled);
        trace.setAnswerQualityJudgeModelName(answerQualityJudgeModelName);
        trace.setAnswerQualityConfidence(Math.max(0, Math.min(100, answerQualityConfidence)));
        trace.setTotalMs(totalMs);
        trace.setErrorMessage(errorMessage);
        return trace;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 600 ? sanitized.substring(0, 600) : sanitized;
    }
}
