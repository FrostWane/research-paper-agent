package com.frostwane.paperagent.agent;

import org.springframework.stereotype.Service;

@Service
public class FormatterAgent {

    public String format(String answer) {
        String value = answer == null ? "" : answer.trim();
        if (value.isBlank()) {
            return "材料不足：Agent 未生成有效回答。";
        }
        return value;
    }
}
