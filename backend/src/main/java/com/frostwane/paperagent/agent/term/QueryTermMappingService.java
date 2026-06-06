package com.frostwane.paperagent.agent.term;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QueryTermMappingService {

    private static final int MAX_MATCHED_MAPPINGS = 8;
    private static final int MAX_QUERY_LENGTH = 1200;

    private final QueryTermMappingRepository repository;

    public QueryTermMappingService(QueryTermMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<QueryTermExpansion> match(String question, String searchQuery) {
        String haystack = normalize((question == null ? "" : question) + " " + (searchQuery == null ? "" : searchQuery));
        if (haystack.isBlank()) {
            return List.of();
        }
        List<QueryTermExpansion> matches = new ArrayList<>();
        for (QueryTermMapping mapping : repository.findByEnabledTrueOrderByTermAsc()) {
            String term = compact(mapping.getTerm(), 120);
            if (term == null || !haystack.contains(normalize(term))) {
                continue;
            }
            List<String> expansions = splitExpansions(mapping.getExpansions());
            if (expansions.isEmpty()) {
                continue;
            }
            matches.add(new QueryTermExpansion(mapping.getId(), term, expansions));
            if (matches.size() >= MAX_MATCHED_MAPPINGS) {
                break;
            }
        }
        return matches;
    }

    public String expandSearchQuery(String searchQuery, List<QueryTermExpansion> matches) {
        if (matches == null || matches.isEmpty()) {
            return searchQuery;
        }
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(compact(searchQuery, MAX_QUERY_LENGTH));
        String normalizedQuery = normalize(searchQuery);
        for (QueryTermExpansion match : matches) {
            for (String expansion : match.expansions()) {
                String normalizedExpansion = normalize(expansion);
                if (normalizedExpansion.isBlank() || normalizedQuery.contains(normalizedExpansion)) {
                    continue;
                }
                tokens.add(expansion);
            }
        }
        String expanded = String.join(" ", tokens.stream().filter(item -> item != null && !item.isBlank()).toList()).trim();
        return expanded.length() <= MAX_QUERY_LENGTH ? expanded : expanded.substring(0, MAX_QUERY_LENGTH);
    }

    public List<String> splitExpansions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Set<String> expansions = new LinkedHashSet<>();
        for (String item : value.split("[,，;；、\\n\\r]+")) {
            String expansion = compact(item, 80);
            if (expansion != null) {
                expansions.add(expansion);
            }
        }
        return List.copyOf(expansions);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
