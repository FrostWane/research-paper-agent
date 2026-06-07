package com.frostwane.paperagent.agent.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseFromTraceRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseResponse;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationDatasetRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationDatasetResponse;
import com.frostwane.paperagent.agent.ChatRecord;
import com.frostwane.paperagent.agent.ChatRecordRepository;
import com.frostwane.paperagent.agent.RagTrace;
import com.frostwane.paperagent.agent.RagTraceRepository;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperRepository;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AgentEvaluationService {

    private static final int MAX_PAGE_SIZE = 80;

    private final EvaluationDatasetRepository datasetRepository;
    private final EvaluationCaseRepository caseRepository;
    private final PaperRepository paperRepository;
    private final ChatRecordRepository chatRecordRepository;
    private final RagTraceRepository ragTraceRepository;
    private final ObjectMapper objectMapper;

    public AgentEvaluationService(
        EvaluationDatasetRepository datasetRepository,
        EvaluationCaseRepository caseRepository,
        PaperRepository paperRepository,
        ChatRecordRepository chatRecordRepository,
        RagTraceRepository ragTraceRepository,
        ObjectMapper objectMapper
    ) {
        this.datasetRepository = datasetRepository;
        this.caseRepository = caseRepository;
        this.paperRepository = paperRepository;
        this.chatRecordRepository = chatRecordRepository;
        this.ragTraceRepository = ragTraceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EvaluationDatasetResponse> datasets(User currentUser) {
        requireAdmin(currentUser);
        return datasetRepository.findAllByOrderByUpdatedAtDescIdDesc()
            .stream()
            .map(this::datasetResponse)
            .toList();
    }

    @Transactional
    public EvaluationDatasetResponse createDataset(EvaluationDatasetRequest request, User currentUser) {
        requireAdmin(currentUser);
        String code = normalizeCode(request.code());
        datasetRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new BusinessException("评测集标识已存在");
        });
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setCreatedBy(currentUser);
        applyDataset(dataset, request, code);
        return datasetResponse(datasetRepository.save(dataset));
    }

    @Transactional
    public EvaluationDatasetResponse updateDataset(Long id, EvaluationDatasetRequest request, User currentUser) {
        requireAdmin(currentUser);
        EvaluationDataset dataset = datasetRepository.findById(id).orElseThrow(() -> new BusinessException("评测集不存在"));
        String code = normalizeCode(request.code());
        datasetRepository.findByCodeIgnoreCase(code)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException("评测集标识已存在");
            });
        applyDataset(dataset, request, code);
        return datasetResponse(datasetRepository.save(dataset));
    }

    @Transactional
    public void deleteDataset(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!datasetRepository.existsById(id)) {
            throw new BusinessException("评测集不存在");
        }
        datasetRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationCaseResponse> cases(
        User currentUser,
        Long datasetId,
        String enabled,
        String keyword,
        int page,
        int pageSize
    ) {
        requireAdmin(currentUser);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, pageSize));
        Page<EvaluationCase> result = caseRepository.findAll(
            caseSpec(datasetId, enabled, keyword),
            PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))
        );
        return new PageResponse<>(
            result.getContent().stream().map(this::caseResponse).toList(),
            result.getTotalElements(),
            safePage,
            safePageSize,
            result.getTotalPages()
        );
    }

    @Transactional
    public EvaluationCaseResponse createCase(EvaluationCaseRequest request, User currentUser) {
        requireAdmin(currentUser);
        EvaluationCase evaluationCase = new EvaluationCase();
        applyCase(evaluationCase, request, currentUser);
        return caseResponse(caseRepository.save(evaluationCase));
    }

    @Transactional
    public EvaluationCaseResponse createCaseFromTrace(EvaluationCaseFromTraceRequest request, User currentUser) {
        requireAdmin(currentUser);
        EvaluationDataset dataset = dataset(request.datasetId());
        RagTrace trace = ragTraceRepository.findById(request.traceId()).orElseThrow(() -> new BusinessException("RAG Trace 不存在"));
        ChatRecord chat = trace.getChatRecord();
        String expectedAnswer = compact(request.expectedAnswer(), 12000);
        if (expectedAnswer == null && chat != null) {
            expectedAnswer = compact(chat.getAnswer(), 12000);
        }
        if (expectedAnswer == null) {
            throw new BusinessException("Trace 未关联可沉淀的回答，请手动填写期望答案");
        }
        EvaluationCase evaluationCase = new EvaluationCase();
        evaluationCase.setDataset(dataset);
        evaluationCase.setSourceOwner(trace.getOwner());
        evaluationCase.setPaper(trace.getPaper());
        evaluationCase.setChatRecord(chat);
        evaluationCase.setRagTrace(trace);
        evaluationCase.setScope(normalizeScope(trace.getScope()));
        evaluationCase.setQuestion(required(trace.getQuestion(), "问题不能为空", 4000));
        evaluationCase.setExpectedAnswer(expectedAnswer);
        evaluationCase.setExpectedSourcesJson(normalizeJson(request.expectedSourcesJson(), chat == null ? "[]" : chat.getSourcesJson()));
        evaluationCase.setTags(compact(request.tags(), 500));
        evaluationCase.setDifficulty(normalizeDifficulty(request.difficulty()));
        evaluationCase.setEnabled(request.enabled() == null || request.enabled());
        return caseResponse(caseRepository.save(evaluationCase));
    }

    @Transactional
    public EvaluationCaseResponse updateCase(Long id, EvaluationCaseRequest request, User currentUser) {
        requireAdmin(currentUser);
        EvaluationCase evaluationCase = caseRepository.findById(id).orElseThrow(() -> new BusinessException("评测样本不存在"));
        applyCase(evaluationCase, request, currentUser);
        return caseResponse(caseRepository.save(evaluationCase));
    }

    @Transactional
    public void deleteCase(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!caseRepository.existsById(id)) {
            throw new BusinessException("评测样本不存在");
        }
        caseRepository.deleteById(id);
    }

    private void applyDataset(EvaluationDataset dataset, EvaluationDatasetRequest request, String code) {
        dataset.setCode(code);
        dataset.setName(required(request.name(), "评测集名称不能为空", 160));
        dataset.setDescription(compact(request.description(), 2000));
        dataset.setScope(normalizeScope(request.scope()));
        dataset.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyCase(EvaluationCase evaluationCase, EvaluationCaseRequest request, User currentUser) {
        EvaluationDataset dataset = dataset(request.datasetId());
        evaluationCase.setDataset(dataset);
        evaluationCase.setQuestion(required(request.question(), "问题不能为空", 4000));
        evaluationCase.setExpectedAnswer(required(request.expectedAnswer(), "期望答案不能为空", 12000));
        evaluationCase.setExpectedSourcesJson(normalizeJson(request.expectedSourcesJson(), "[]"));
        evaluationCase.setTags(compact(request.tags(), 500));
        evaluationCase.setDifficulty(normalizeDifficulty(request.difficulty()));
        evaluationCase.setEnabled(request.enabled() == null || request.enabled());

        RagTrace trace = request.ragTraceId() == null ? null : ragTraceRepository.findById(request.ragTraceId())
            .orElseThrow(() -> new BusinessException("RAG Trace 不存在"));
        ChatRecord chat = request.chatRecordId() == null ? null : chatRecordRepository.findById(request.chatRecordId())
            .orElseThrow(() -> new BusinessException("问答记录不存在"));
        Paper paper = request.paperId() == null ? null : paperRepository.findById(request.paperId())
            .orElseThrow(() -> new BusinessException("文献不存在"));

        evaluationCase.setRagTrace(trace);
        evaluationCase.setChatRecord(chat);
        evaluationCase.setPaper(firstNonNull(paper, trace == null ? null : trace.getPaper(), chat == null ? null : chat.getPaper()));
        evaluationCase.setSourceOwner(firstNonNull(trace == null ? null : trace.getOwner(), chat == null ? null : chat.getOwner(), currentUser));
        evaluationCase.setScope(normalizeScope(firstNonBlank(request.scope(), trace == null ? null : trace.getScope(), dataset.getScope())));
    }

    private Specification<EvaluationCase> caseSpec(Long datasetId, String enabled, String keyword) {
        return (root, query, builder) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("dataset", JoinType.LEFT);
                root.fetch("sourceOwner", JoinType.LEFT);
                root.fetch("paper", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (datasetId != null) {
                predicates.add(builder.equal(root.get("dataset").get("id"), datasetId));
            }
            if ("true".equalsIgnoreCase(enabled) || "false".equalsIgnoreCase(enabled)) {
                predicates.add(builder.equal(root.get("enabled"), Boolean.parseBoolean(enabled)));
            }
            String normalizedKeyword = compact(keyword, 120);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("question")), pattern),
                    builder.like(builder.lower(root.get("expectedAnswer")), pattern),
                    builder.like(builder.lower(root.get("tags")), pattern),
                    builder.like(builder.lower(root.get("dataset").get("code")), pattern),
                    builder.like(builder.lower(root.get("dataset").get("name")), pattern)
                ));
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

    private EvaluationDatasetResponse datasetResponse(EvaluationDataset dataset) {
        return new EvaluationDatasetResponse(
            dataset.getId(),
            dataset.getCode(),
            dataset.getName(),
            dataset.getDescription(),
            dataset.getScope(),
            Boolean.TRUE.equals(dataset.getEnabled()),
            caseRepository.countByDatasetId(dataset.getId()),
            caseRepository.countByDatasetIdAndEnabledTrue(dataset.getId()),
            dataset.getCreatedBy() == null ? null : dataset.getCreatedBy().getUsername(),
            dataset.getCreatedAt(),
            dataset.getUpdatedAt()
        );
    }

    private EvaluationCaseResponse caseResponse(EvaluationCase item) {
        EvaluationDataset dataset = item.getDataset();
        User owner = item.getSourceOwner();
        Paper paper = item.getPaper();
        return new EvaluationCaseResponse(
            item.getId(),
            dataset == null ? null : dataset.getId(),
            dataset == null ? null : dataset.getCode(),
            dataset == null ? null : dataset.getName(),
            owner == null ? null : owner.getUsername(),
            paper == null ? null : paper.getId(),
            paper == null ? null : paper.getTitle(),
            item.getChatRecord() == null ? null : item.getChatRecord().getId(),
            item.getRagTrace() == null ? null : item.getRagTrace().getId(),
            item.getScope(),
            item.getQuestion(),
            item.getExpectedAnswer(),
            item.getExpectedSourcesJson(),
            item.getTags(),
            item.getDifficulty(),
            Boolean.TRUE.equals(item.getEnabled()),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }

    private String normalizeJson(String value, String fallback) {
        String candidate = compact(value, 20000);
        if (candidate == null) {
            candidate = compact(fallback, 20000);
        }
        if (candidate == null) {
            return "[]";
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            if (!node.isArray() && !node.isObject()) {
                throw new BusinessException("期望来源 JSON 必须是数组或对象");
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("期望来源 JSON 格式不正确");
        }
    }

    private String normalizeScope(String value) {
        String scope = value == null || value.isBlank() ? "LIBRARY" : value.trim().toUpperCase(Locale.ROOT);
        if ("PAPER".equals(scope) || "LIBRARY".equals(scope)) {
            return scope;
        }
        throw new BusinessException("评测范围只能是 PAPER 或 LIBRARY");
    }

    private String normalizeDifficulty(String value) {
        String difficulty = value == null || value.isBlank() ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        if ("EASY".equals(difficulty) || "MEDIUM".equals(difficulty) || "HARD".equals(difficulty)) {
            return difficulty;
        }
        throw new BusinessException("评测难度只能是 EASY、MEDIUM 或 HARD");
    }

    private String normalizeCode(String value) {
        String normalized = required(value, "评测集标识不能为空", 64)
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_]+", "_")
            .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException("评测集标识不能为空");
        }
        return normalized;
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private String required(String value, String message, int maxLength) {
        String compacted = compact(value, maxLength);
        if (compacted == null) {
            throw new BusinessException(message);
        }
        return compacted;
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
}
