package com.frostwane.paperagent.agent.token;

import org.springframework.stereotype.Service;

@Service
public class HeuristicTokenCounterService implements TokenCounterService {

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        int latinRun = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                tokens += latinTokens(latinRun) + 1;
                latinRun = 0;
            } else if (Character.isLetterOrDigit(codePoint)) {
                latinRun++;
            } else {
                tokens += latinTokens(latinRun);
                latinRun = 0;
                if (!Character.isWhitespace(codePoint)) {
                    tokens++;
                }
            }
        }
        return tokens + latinTokens(latinRun);
    }

    @Override
    public String truncateToTokenBudget(String text, int tokenBudget) {
        if (text == null || text.isBlank() || tokenBudget <= 0) {
            return "";
        }
        if (estimateTokens(text) <= tokenBudget) {
            return text.trim();
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (estimateTokens(text.substring(0, mid)) <= tokenBudget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, Math.max(0, low)).trim();
    }

    private int latinTokens(int length) {
        if (length <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(length / 4.0d));
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
            || script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA
            || script == Character.UnicodeScript.HANGUL;
    }
}
