package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.agent.retrieval.MultiChannelRetrievalEngine;
import com.frostwane.paperagent.agent.retrieval.RetrievalRequest;
import com.frostwane.paperagent.agent.retrieval.RetrievalResult;
import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrieverAgent {

    private final MultiChannelRetrievalEngine retrievalEngine;
    private final RagSettingsService ragSettingsService;

    public RetrieverAgent(MultiChannelRetrievalEngine retrievalEngine, RagSettingsService ragSettingsService) {
        this.retrievalEngine = retrievalEngine;
        this.ragSettingsService = ragSettingsService;
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
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        return retrievalEngine.retrieve(new RetrievalRequest(paper, paper.getOwner(), question, false, settings.candidateLimit(), settings), settings.resultLimit());
    }

    public RetrievalResult retrieveLibraryWithDiagnostics(User owner, String question, boolean useRag) {
        if (!useRag) {
            return RetrievalResult.empty();
        }
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        return retrievalEngine.retrieve(new RetrievalRequest(null, owner, question, true, settings.candidateLimit(), settings), settings.resultLimit());
    }
}
