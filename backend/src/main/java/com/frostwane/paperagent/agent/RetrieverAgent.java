package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.retrieval.MultiChannelRetrievalEngine;
import com.frostwane.paperagent.agent.retrieval.RetrievalRequest;
import com.frostwane.paperagent.agent.retrieval.RetrievalResult;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrieverAgent {

    private static final int RESULT_LIMIT = 5;
    private static final int CANDIDATE_LIMIT = 10;

    private final MultiChannelRetrievalEngine retrievalEngine;

    public RetrieverAgent(MultiChannelRetrievalEngine retrievalEngine) {
        this.retrievalEngine = retrievalEngine;
    }

    public List<SourceResponse> retrieve(Paper paper, String question, boolean useRag) {
        return retrieveWithDiagnostics(paper, question, useRag).sources();
    }

    public List<SourceResponse> retrieveLibrary(User owner, String question, boolean useRag) {
        return retrieveLibraryWithDiagnostics(owner, question, useRag).sources();
    }

    public RetrievalResult retrieveWithDiagnostics(Paper paper, String question, boolean useRag) {
        if (!useRag) {
            return RetrievalResult.empty();
        }
        return retrievalEngine.retrieve(new RetrievalRequest(paper, paper.getOwner(), question, false, CANDIDATE_LIMIT), RESULT_LIMIT);
    }

    public RetrievalResult retrieveLibraryWithDiagnostics(User owner, String question, boolean useRag) {
        if (!useRag) {
            return RetrievalResult.empty();
        }
        return retrievalEngine.retrieve(new RetrievalRequest(null, owner, question, true, CANDIDATE_LIMIT), RESULT_LIMIT);
    }
}
