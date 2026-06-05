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
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
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
            sourceCount,
            retrievalMs,
            generationMs,
            verificationMs,
            formattingMs,
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
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
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
            sourceCount,
            retrievalMs,
            generationMs,
            verificationMs,
            formattingMs,
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
        int sourceCount,
        int retrievalMs,
        int generationMs,
        int verificationMs,
        int formattingMs,
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
        trace.setSourceCount(sourceCount);
        trace.setRetrievalMs(retrievalMs);
        trace.setGenerationMs(generationMs);
        trace.setVerificationMs(verificationMs);
        trace.setFormattingMs(formattingMs);
        trace.setTotalMs(totalMs);
        trace.setErrorMessage(errorMessage);
        return trace;
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 600 ? sanitized.substring(0, 600) : sanitized;
    }
}
