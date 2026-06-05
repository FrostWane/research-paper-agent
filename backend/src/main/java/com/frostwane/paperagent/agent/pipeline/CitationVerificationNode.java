package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.agent.CitationVerifierAgent;
import org.springframework.stereotype.Component;

@Component
public class CitationVerificationNode implements AgentNode {

    private final CitationVerifierAgent citationVerifierAgent;

    public CitationVerificationNode(CitationVerifierAgent citationVerifierAgent) {
        this.citationVerifierAgent = citationVerifierAgent;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.VERIFICATION;
    }

    @Override
    public String name() {
        return "citation-verification";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        context.verifiedAnswer(citationVerifierAgent.verify(context.generatedAnswer(), context.sources()));
    }
}
