package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.RetrieverAgent;
import com.frostwane.paperagent.agent.retrieval.RetrievalResult;
import org.springframework.stereotype.Component;

@Component
public class RetrievalNode implements AgentNode {

    private final RetrieverAgent retrieverAgent;

    public RetrievalNode(RetrieverAgent retrieverAgent) {
        this.retrieverAgent = retrieverAgent;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.RETRIEVAL;
    }

    @Override
    public String name() {
        return "retrieval";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        RetrievalResult result = context.libraryScope()
            ? retrieverAgent.retrieveLibraryWithDiagnostics(context.owner(), context.searchQuery(), context.useRag())
            : retrieverAgent.retrieveWithDiagnostics(context.paper(), context.searchQuery(), context.useRag());
        context.sources(result.sources());
        context.retrievalChannels(result.channels());
    }
}
