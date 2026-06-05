package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.RetrieverAgent;
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
        context.sources(context.libraryScope()
            ? retrieverAgent.retrieveLibrary(context.owner(), context.question(), context.useRag())
            : retrieverAgent.retrieve(context.paper(), context.question(), context.useRag()));
    }
}
