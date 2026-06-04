package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CitationVerifierAgent {

    public String verify(String answer, List<SourceResponse> sources) {
        if (sources.isEmpty()) {
            if (answer.contains("材料不足")) {
                return answer;
            }
            return answer + "\n\n> 材料不足：当前回答未命中 PDF 正文片段，不能替代正式精读。";
        }
        boolean hasPageSignal = answer.contains("第 ") || answer.contains("页") || answer.toLowerCase().contains("page");
        if (hasPageSignal) {
            return answer;
        }
        StringBuilder builder = new StringBuilder(answer).append("\n\n## 引用页码\n\n");
        sources.forEach(source -> builder.append("- 第 ").append(source.page()).append(" 页\n"));
        return builder.toString();
    }
}
