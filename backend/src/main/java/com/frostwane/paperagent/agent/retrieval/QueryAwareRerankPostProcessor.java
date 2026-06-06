package com.frostwane.paperagent.agent.retrieval;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class QueryAwareRerankPostProcessor implements RetrievalPostProcessor {

    private static final Set<String> STOP_WORDS = Set.of(
        "these", "this", "that", "paper", "papers", "article", "articles",
        "cross", "please", "help", "what", "how", "and", "the", "with",
        "这些", "这篇", "论文", "文献", "文章", "请", "帮我", "一下", "主要"
    );

    @Override
    public String name() {
        return "query-aware-rerank";
    }

    @Override
    public String label() {
        return "精排";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public boolean supports(RetrievalProcessingContext context) {
        return context.request().query() != null && !context.request().query().isBlank();
    }

    @Override
    public List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        Set<String> queryTerms = tokenize(context.request().query());
        if (queryTerms.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
            .map(candidate -> rerank(candidate, queryTerms))
            .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed()
                .thenComparing(RetrievalCandidate::title)
                .thenComparingInt(RetrievalCandidate::pageNumber)
                .thenComparingInt(RetrievalCandidate::chunkIndex))
            .toList();
    }

    private RetrievalCandidate rerank(RetrievalCandidate candidate, Set<String> queryTerms) {
        String title = normalize(candidate.title());
        String content = normalize(candidate.content());
        double lexicalBoost = 0.0d;
        int matched = 0;

        for (String term : queryTerms) {
            if (title.contains(term)) {
                lexicalBoost += 0.16d;
                matched++;
            } else if (content.contains(term)) {
                lexicalBoost += 0.07d;
                matched++;
            }
        }

        if (matched >= 3) {
            lexicalBoost += 0.12d;
        }
        if (candidate.channelName() != null && candidate.channelName().contains("+")) {
            lexicalBoost += 0.08d;
        }

        return new RetrievalCandidate(
            candidate.chunkId(),
            candidate.paperId(),
            candidate.title(),
            candidate.pageNumber(),
            candidate.chunkIndex(),
            candidate.content(),
            candidate.score() + Math.min(lexicalBoost, 1.0d),
            candidate.channelName()
        );
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalize(text).split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            if (isCjk(token)) {
                collectCjkTerms(token, tokens);
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void collectCjkTerms(String token, Set<String> tokens) {
        if (token.length() <= 6 && !STOP_WORDS.contains(token)) {
            tokens.add(token);
        }
        for (int i = 0; i < token.length() - 1; i++) {
            String term = token.substring(i, i + 2);
            if (!STOP_WORDS.contains(term)) {
                tokens.add(term);
            }
        }
    }

    private boolean isCjk(String token) {
        return token.chars().anyMatch(ch -> ch >= '\u4e00' && ch <= '\u9fa5');
    }

    private String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT);
    }
}
