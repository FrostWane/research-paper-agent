package com.frostwane.paperagent.agent.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseResultResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationRunRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationRunResponse;
import com.frostwane.paperagent.agent.AgentOrchestratorService;
import com.frostwane.paperagent.agent.AnswerQualityAgent;
import com.frostwane.paperagent.agent.AnswerQualityAgent.AnswerQualityAssessment;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AgentEvaluationRunService {

    private static final int MAX_PAGE_SIZE = 80;

    private final EvaluationDatasetRepository datasetRepository;
    private final EvaluationCaseRepository caseRepository;
    private final EvaluationRunRepository runRepository;
    private final EvaluationCaseResultRepository resultRepository;
    private final AgentOrchestratorService orchestratorService;
    private final AnswerQualityAgent answerQualityAgent;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor evaluationTaskExecutor;
    private final TransactionTemplate transactionTemplate;

    public AgentEvaluationRunService(
        EvaluationDatasetRepository datasetRepository,
        EvaluationCaseRepository caseRepository,
        EvaluationRunRepository runRepository,
        EvaluationCaseResultRepository resultRepository,
        AgentOrchestratorService orchestratorService,
        AnswerQualityAgent answerQualityAgent,
        ObjectMapper objectMapper,
        @Qualifier("evaluationTaskExecutor") ThreadPoolTaskExecutor evaluationTaskExecutor,
        TransactionTemplate transactionTemplate
    ) {
        this.datasetRepository = datasetRepository;
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.orchestratorService = orchestratorService;
        this.answerQualityAgent = answerQualityAgent;
        this.objectMapper = objectMapper;
        this.evaluationTaskExecutor = evaluationTaskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public EvaluationRunResponse startRun(EvaluationRunRequest request, User currentUser) {
        requireAdmin(currentUser);
        EvaluationRun run = transactionTemplate.execute(status -> {
            EvaluationDataset dataset = dataset(request.datasetId());
            long enabledCases = caseRepository.countByDatasetIdAndEnabledTrue(dataset.getId());
            if (enabledCases <= 0) {
                throw new BusinessException("评测集没有启用样本，无法运行");
            }
            EvaluationRun item = new EvaluationRun();
            item.setDataset(dataset);
            item.setTriggeredBy(currentUser);
            item.setRunName(compact(request.runName(), 180));
            item.setStatus("QUEUED");
            item.setCaseCount(Math.toIntExact(Math.min(enabledCases, Integer.MAX_VALUE)));
            return runRepository.save(item);
        });
        try {
            evaluationTaskExecutor.execute(() -> executeRun(run.getId()));
        } catch (RuntimeException ex) {
            markRunFailed(run.getId(), sanitize(ex));
            throw ex;
        }
        return runResponse(run);
    }

    public PageResponse<EvaluationRunResponse> runs(User currentUser, Long datasetId, String status, int page, int pageSize) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, pageSize));
        Page<EvaluationRun> result = runRepository.findAll(
            runSpec(datasetId, status),
            PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        );
        return new PageResponse<>(
            result.getContent().stream().map(this::runResponse).toList(),
            result.getTotalElements(),
            safePage,
            safePageSize,
            result.getTotalPages()
        );
    }

    public PageResponse<EvaluationCaseResultResponse> results(User currentUser, Long runId, int page, int pageSize) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, pageSize));
        if (!runRepository.existsById(runId)) {
            throw new BusinessException("评测运行不存在");
        }
        Page<EvaluationCaseResult> result = resultRepository.findByRunIdOrderByIdAsc(
            runId,
            PageRequest.of(safePage - 1, safePageSize)
        );
        return new PageResponse<>(
            result.getContent().stream().map(this::resultResponse).toList(),
            result.getTotalElements(),
            safePage,
            safePageSize,
            result.getTotalPages()
        );
    }

    private void executeRun(Long runId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        List<Long> caseIds = transactionTemplate.execute(status -> {
            EvaluationRun run = runRepository.findById(runId).orElseThrow(() -> new BusinessException("评测运行不存在"));
            run.setStatus("RUNNING");
            run.setStartedAt(startedAt);
            run.setErrorMessage(null);
            return caseRepository.findByDatasetIdAndEnabledTrueOrderByIdAsc(run.getDataset().getId())
                .stream()
                .map(EvaluationCase::getId)
                .toList();
        });
        try {
            for (Long caseId : caseIds) {
                WorkItem item = transactionTemplate.execute(status -> workItem(runId, caseId));
                runCase(item);
            }
            finishRun(runId, "SUCCESS", null, startedAt);
        } catch (RuntimeException ex) {
            finishRun(runId, "FAILED", sanitize(ex), startedAt);
        }
    }

    private WorkItem workItem(Long runId, Long caseId) {
        EvaluationRun run = runRepository.findById(runId).orElseThrow(() -> new BusinessException("评测运行不存在"));
        EvaluationCase item = caseRepository.findById(caseId).orElseThrow(() -> new BusinessException("评测样本不存在"));
        Paper paper = item.getPaper();
        User owner = firstNonNull(item.getSourceOwner(), paper == null ? null : paper.getOwner(), run.getTriggeredBy());
        if (owner == null) {
            throw new BusinessException("评测样本缺少可运行的用户上下文");
        }
        Long paperId = paper == null ? null : paper.getId();
        if ("PAPER".equalsIgnoreCase(item.getScope()) && paperId == null) {
            throw new BusinessException("单篇评测样本缺少文献关联");
        }
        return new WorkItem(
            runId,
            item.getId(),
            owner,
            paperId,
            item.getQuestion(),
            item.getExpectedAnswer(),
            item.getExpectedSourcesJson()
        );
    }

    private void runCase(WorkItem item) {
        try {
            ChatResponse response = orchestratorService.evaluate(new ChatRequest(
                null,
                item.paperId(),
                item.question(),
                true
            ), item.owner());
            String actualSourcesJson = toJson(response.sources());
            EvaluationScore score = score(item.question(), item.expectedAnswer(), response.answer(), item.expectedSourcesJson(), actualSourcesJson, response.sources());
            saveResult(item, response.answer(), actualSourcesJson, response.latencyMs(), response.modelName(), score, null);
        } catch (RuntimeException ex) {
            saveResult(item, null, "[]", 0, null, EvaluationScore.error(), sanitize(ex));
        }
    }

    private EvaluationScore score(
        String question,
        String expectedAnswer,
        String actualAnswer,
        String expectedSourcesJson,
        String actualSourcesJson,
        List<SourceResponse> actualSources
    ) {
        double answerSimilarity = answerSimilarity(expectedAnswer, actualAnswer);
        SourceMatch sourceMatch = sourceMatch(expectedSourcesJson, actualSourcesJson);
        AnswerQualityAssessment quality = answerQualityAgent.evaluate(question, actualAnswer, actualSources, false);
        int score = clamp((int) Math.round(answerSimilarity * 0.55 + sourceMatch.coverage() * 0.25 + quality.score() * 0.20), 0, 100);
        String status = score >= 70 ? "PASS" : score >= 45 ? "REVIEW" : "FAIL";
        return new EvaluationScore(
            status,
            score,
            round2(answerSimilarity),
            round2(sourceMatch.coverage()),
            sourceMatch.matched(),
            sourceMatch.expected()
        );
    }

    private void saveResult(
        WorkItem item,
        String actualAnswer,
        String actualSourcesJson,
        int latencyMs,
        String modelName,
        EvaluationScore score,
        String errorMessage
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            EvaluationRun run = runRepository.findById(item.runId()).orElseThrow(() -> new BusinessException("评测运行不存在"));
            EvaluationCase evaluationCase = caseRepository.findById(item.caseId()).orElse(null);
            EvaluationCaseResult result = new EvaluationCaseResult();
            result.setRun(run);
            result.setEvaluationCase(evaluationCase);
            result.setStatus(errorMessage == null ? score.status() : "ERROR");
            result.setScore(errorMessage == null ? score.score() : 0);
            result.setAnswerSimilarity(errorMessage == null ? score.answerSimilarity() : 0.0);
            result.setSourceCoverage(errorMessage == null ? score.sourceCoverage() : 0.0);
            result.setMatchedSources(errorMessage == null ? score.matchedSources() : 0);
            result.setExpectedSources(errorMessage == null ? score.expectedSources() : sourceCount(item.expectedSourcesJson()));
            result.setExpectedAnswer(item.expectedAnswer());
            result.setActualAnswer(actualAnswer);
            result.setExpectedSourcesJson(defaultJson(item.expectedSourcesJson()));
            result.setActualSourcesJson(defaultJson(actualSourcesJson));
            result.setLatencyMs(Math.max(0, latencyMs));
            result.setModelName(compact(modelName, 160));
            result.setErrorMessage(errorMessage);
            resultRepository.save(result);
            updateRunProgress(run, result);
        });
    }

    private void updateRunProgress(EvaluationRun run, EvaluationCaseResult result) {
        int completed = safeInt(run.getCompletedCount()) + 1;
        int passed = safeInt(run.getPassedCount()) + ("PASS".equals(result.getStatus()) ? 1 : 0);
        double previousAverage = run.getAverageScore() == null ? 0 : run.getAverageScore();
        double averageScore = ((previousAverage * (completed - 1)) + safeInt(result.getScore())) / completed;
        run.setCompletedCount(completed);
        run.setPassedCount(passed);
        run.setAverageScore(round2(averageScore));
    }

    private void finishRun(Long runId, String status, String errorMessage, OffsetDateTime startedAt) {
        transactionTemplate.executeWithoutResult(tx -> {
            EvaluationRun run = runRepository.findById(runId).orElseThrow(() -> new BusinessException("评测运行不存在"));
            OffsetDateTime finishedAt = OffsetDateTime.now();
            run.setStatus(status);
            run.setFinishedAt(finishedAt);
            run.setDurationMs(Math.toIntExact(Math.min(Duration.between(startedAt, finishedAt).toMillis(), Integer.MAX_VALUE)));
            run.setErrorMessage(compact(errorMessage, 2000));
        });
    }

    private void markRunFailed(Long runId, String errorMessage) {
        transactionTemplate.executeWithoutResult(tx -> {
            runRepository.findById(runId).ifPresent(run -> {
                run.setStatus("FAILED");
                run.setFinishedAt(OffsetDateTime.now());
                run.setErrorMessage(compact(errorMessage, 2000));
            });
        });
    }

    private Specification<EvaluationRun> runSpec(Long datasetId, String status) {
        return (root, query, builder) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("dataset", JoinType.LEFT);
                root.fetch("triggeredBy", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (datasetId != null) {
                predicates.add(builder.equal(root.get("dataset").get("id"), datasetId));
            }
            String normalizedStatus = compact(status, 32);
            if (normalizedStatus != null) {
                predicates.add(builder.equal(root.get("status"), normalizedStatus.toUpperCase(Locale.ROOT)));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private EvaluationDataset dataset(Long id) {
        if (id == null) {
            throw new BusinessException("评测集 ID 不能为空");
        }
        return datasetRepository.findById(id).orElseThrow(() -> new BusinessException("评测集不存在"));
    }

    private EvaluationRunResponse runResponse(EvaluationRun run) {
        EvaluationDataset dataset = run.getDataset();
        User triggeredBy = run.getTriggeredBy();
        return new EvaluationRunResponse(
            run.getId(),
            dataset == null ? null : dataset.getId(),
            dataset == null ? null : dataset.getCode(),
            dataset == null ? null : dataset.getName(),
            triggeredBy == null ? null : triggeredBy.getUsername(),
            run.getRunName(),
            run.getStatus(),
            safeInt(run.getCaseCount()),
            safeInt(run.getCompletedCount()),
            safeInt(run.getPassedCount()),
            run.getAverageScore() == null ? 0 : run.getAverageScore(),
            run.getStartedAt(),
            run.getFinishedAt(),
            safeInt(run.getDurationMs()),
            run.getErrorMessage(),
            run.getCreatedAt(),
            run.getUpdatedAt()
        );
    }

    private EvaluationCaseResultResponse resultResponse(EvaluationCaseResult result) {
        EvaluationCase item = result.getEvaluationCase();
        return new EvaluationCaseResultResponse(
            result.getId(),
            result.getRun() == null ? null : result.getRun().getId(),
            item == null ? null : item.getId(),
            item == null ? null : item.getQuestion(),
            item == null || item.getDataset() == null ? null : item.getDataset().getCode(),
            result.getStatus(),
            safeInt(result.getScore()),
            result.getAnswerSimilarity() == null ? 0 : result.getAnswerSimilarity(),
            result.getSourceCoverage() == null ? 0 : result.getSourceCoverage(),
            safeInt(result.getMatchedSources()),
            safeInt(result.getExpectedSources()),
            result.getExpectedAnswer(),
            result.getActualAnswer(),
            result.getExpectedSourcesJson(),
            result.getActualSourcesJson(),
            safeInt(result.getLatencyMs()),
            result.getModelName(),
            result.getErrorMessage(),
            result.getCreatedAt()
        );
    }

    private double answerSimilarity(String expected, String actual) {
        Set<String> expectedGrams = grams(expected);
        Set<String> actualGrams = grams(actual);
        if (expectedGrams.isEmpty() || actualGrams.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(expectedGrams);
        intersection.retainAll(actualGrams);
        Set<String> union = new HashSet<>(expectedGrams);
        union.addAll(actualGrams);
        return union.isEmpty() ? 0 : (intersection.size() * 100.0 / union.size());
    }

    private Set<String> grams(String value) {
        String normalized = defaultText(value, "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "")
            .replaceAll("[，。！？；：、,.!?;:'\"`~\\[\\]{}()（）<>《》]+", "");
        if (normalized.length() > 3000) {
            normalized = normalized.substring(0, 3000);
        }
        Set<String> grams = new HashSet<>();
        if (normalized.length() <= 2) {
            if (!normalized.isBlank()) {
                grams.add(normalized);
            }
            return grams;
        }
        for (int i = 0; i <= normalized.length() - 2; i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        return grams;
    }

    private SourceMatch sourceMatch(String expectedJson, String actualJson) {
        List<SourceFingerprint> expected = sourceFingerprints(expectedJson);
        List<SourceFingerprint> actual = sourceFingerprints(actualJson);
        if (expected.isEmpty()) {
            return new SourceMatch(0, 0, 100);
        }
        int matched = 0;
        for (SourceFingerprint item : expected) {
            boolean hit = actual.stream().anyMatch(candidate -> candidate.matches(item));
            if (hit) {
                matched++;
            }
        }
        return new SourceMatch(expected.size(), matched, matched * 100.0 / expected.size());
    }

    private List<SourceFingerprint> sourceFingerprints(String json) {
        try {
            JsonNode root = objectMapper.readTree(defaultJson(json));
            List<SourceFingerprint> items = new ArrayList<>();
            collectSources(root, items);
            return items.stream().distinct().limit(80).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void collectSources(JsonNode node, List<SourceFingerprint> items) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectSources(child, items));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("sources")) {
            collectSources(node.get("sources"), items);
        }
        Long paperId = node.hasNonNull("paperId") ? node.get("paperId").asLong() : null;
        String title = node.hasNonNull("title") ? compact(node.get("title").asText(), 160) : null;
        int page = node.hasNonNull("page") ? node.get("page").asInt(0) : 0;
        if (paperId != null || page > 0 || title != null) {
            items.add(new SourceFingerprint(paperId, title, page));
        }
    }

    private int sourceCount(String json) {
        return sourceFingerprints(json).size();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String defaultJson(String value) {
        String compacted = compact(value, 20000);
        return compacted == null ? "[]" : compacted;
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private String sanitize(Exception ex) {
        String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return compact(message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***"), 2000);
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record WorkItem(
        Long runId,
        Long caseId,
        User owner,
        Long paperId,
        String question,
        String expectedAnswer,
        String expectedSourcesJson
    ) {
    }

    private record EvaluationScore(
        String status,
        int score,
        double answerSimilarity,
        double sourceCoverage,
        int matchedSources,
        int expectedSources
    ) {
        private static EvaluationScore error() {
            return new EvaluationScore("ERROR", 0, 0, 0, 0, 0);
        }
    }

    private record SourceMatch(int expected, int matched, double coverage) {
    }

    private record SourceFingerprint(Long paperId, String title, int page) {
        private boolean matches(SourceFingerprint expected) {
            if (expected.paperId() != null && paperId() != null) {
                return Objects.equals(expected.paperId(), paperId()) && samePage(expected.page(), page());
            }
            if (expected.title() != null && title() != null && samePage(expected.page(), page())) {
                return expected.title().equalsIgnoreCase(title());
            }
            return expected.page() > 0 && samePage(expected.page(), page());
        }

        private boolean samePage(int left, int right) {
            return left <= 0 || right <= 0 || left == right;
        }
    }
}
