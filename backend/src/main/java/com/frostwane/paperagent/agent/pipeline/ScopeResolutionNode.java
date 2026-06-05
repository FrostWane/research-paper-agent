package com.frostwane.paperagent.agent.pipeline;

import com.frostwane.paperagent.paper.PaperService;
import org.springframework.stereotype.Component;

@Component
public class ScopeResolutionNode implements AgentNode {

    private final PaperService paperService;

    public ScopeResolutionNode(PaperService paperService) {
        this.paperService = paperService;
    }

    @Override
    public AgentNodeType type() {
        return AgentNodeType.SCOPE_RESOLUTION;
    }

    @Override
    public String name() {
        return "scope-resolution";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void execute(AgentPipelineContext context) {
        if (context.libraryScope()) {
            return;
        }
        context.paper(paperService.requireOwnedPaper(context.request().paperId(), context.owner().getId()));
    }
}
