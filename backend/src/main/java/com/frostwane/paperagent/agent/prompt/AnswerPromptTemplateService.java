package com.frostwane.paperagent.agent.prompt;

import com.frostwane.paperagent.admin.dto.AdminDtos.AnswerPromptTemplateRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.AnswerPromptTemplateResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnswerPromptTemplateService {

    private static final String DEFAULT_CODE = "BUILTIN_ACADEMIC_RAG";

    private final AnswerPromptTemplateRepository repository;

    public AnswerPromptTemplateService(AnswerPromptTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RenderedAnswerPrompt render(
        Paper paper,
        String question,
        List<SourceResponse> sources,
        String conversationHistory,
        String answerStrategy,
        String answerContract
    ) {
        return render(paper, question, sources, conversationHistory, "", answerStrategy, answerContract);
    }

    @Transactional(readOnly = true)
    public RenderedAnswerPrompt render(
        Paper paper,
        String question,
        List<SourceResponse> sources,
        String conversationHistory,
        String toolContext,
        String answerStrategy,
        String answerContract
    ) {
        AnswerPromptTemplate template = currentTemplate();
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("scope", paper == null ? "当前用户的整个文献库。" : "单篇论文精读。");
        slots.put("paper_metadata", paperMetadata(paper));
        slots.put("answer_strategy", defaultText(answerStrategy, "EVIDENCE_GROUNDED_QA"));
        slots.put("answer_contract", defaultText(answerContract, "按证据回答，材料不足时明确边界。"));
        slots.put("conversation_history", defaultText(conversationHistory, "无历史对话。"));
        slots.put("tool_context", defaultText(toolContext, "无业务工具结果。"));
        slots.put("question", defaultText(question, ""));
        slots.put("sources", sourceText(sources));
        return new RenderedAnswerPrompt(
            template == null ? DEFAULT_CODE : template.getCode(),
            cleanup(template == null ? builtinSystemPrompt() : template.getSystemPrompt()),
            cleanup(fill(template == null ? builtinUserPromptTemplate() : template.getUserPromptTemplate(), slots))
        );
    }

    @Transactional(readOnly = true)
    public List<AnswerPromptTemplateResponse> list(User currentUser) {
        requireAdmin(currentUser);
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(this::response)
            .toList();
    }

    @Transactional
    public AnswerPromptTemplateResponse create(AnswerPromptTemplateRequest request, User currentUser) {
        requireAdmin(currentUser);
        String code = normalizeCode(request.code());
        repository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new BusinessException("回答模板标识已存在");
        });
        AnswerPromptTemplate template = new AnswerPromptTemplate();
        apply(template, request, code);
        AnswerPromptTemplate saved = repository.save(template);
        if (Boolean.TRUE.equals(saved.getDefaultTemplate())) {
            repository.clearDefaultExcept(saved.getId());
        }
        return response(saved);
    }

    @Transactional
    public AnswerPromptTemplateResponse update(Long id, AnswerPromptTemplateRequest request, User currentUser) {
        requireAdmin(currentUser);
        AnswerPromptTemplate template = repository.findById(id).orElseThrow(() -> new BusinessException("回答模板不存在"));
        String code = normalizeCode(request.code());
        repository.findByCodeIgnoreCase(code)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException("回答模板标识已存在");
            });
        apply(template, request, code);
        AnswerPromptTemplate saved = repository.save(template);
        if (Boolean.TRUE.equals(saved.getDefaultTemplate())) {
            repository.clearDefaultExcept(saved.getId());
        }
        return response(saved);
    }

    @Transactional
    public void delete(Long id, User currentUser) {
        requireAdmin(currentUser);
        if (!repository.existsById(id)) {
            throw new BusinessException("回答模板不存在");
        }
        repository.deleteById(id);
    }

    private AnswerPromptTemplate currentTemplate() {
        return repository.findFirstByEnabledTrueAndDefaultTemplateTrueOrderBySortOrderAscIdAsc()
            .or(() -> repository.findFirstByEnabledTrueOrderBySortOrderAscIdAsc())
            .orElse(null);
    }

    private void apply(AnswerPromptTemplate template, AnswerPromptTemplateRequest request, String code) {
        String name = compact(request.name(), 120);
        String systemPrompt = compact(request.systemPrompt(), 8000);
        String userPromptTemplate = compact(request.userPromptTemplate(), 12000);
        if (code == null || name == null || systemPrompt == null || userPromptTemplate == null) {
            throw new BusinessException("模板标识、名称、System Prompt 和 User Prompt 模板不能为空");
        }
        template.setCode(code);
        template.setName(name);
        template.setDescription(compact(request.description(), 500));
        template.setSystemPrompt(systemPrompt);
        template.setUserPromptTemplate(userPromptTemplate);
        boolean defaultTemplate = Boolean.TRUE.equals(request.defaultTemplate());
        boolean enabled = defaultTemplate || request.enabled() == null || request.enabled();
        template.setEnabled(enabled);
        template.setDefaultTemplate(enabled && defaultTemplate);
        template.setSortOrder(clamp(request.sortOrder(), 0, 1000, 100));
    }

    private AnswerPromptTemplateResponse response(AnswerPromptTemplate template) {
        return new AnswerPromptTemplateResponse(
            template.getId(),
            template.getCode(),
            template.getName(),
            template.getDescription(),
            template.getSystemPrompt(),
            template.getUserPromptTemplate(),
            Boolean.TRUE.equals(template.getEnabled()),
            Boolean.TRUE.equals(template.getDefaultTemplate()),
            template.getSortOrder() == null ? 100 : template.getSortOrder(),
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }

    private String fill(String template, Map<String, String> slots) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String paperMetadata(Paper paper) {
        if (paper == null) {
            return "单篇题录：无。";
        }
        return """
            文献标题：%s
            作者：%s
            会议/期刊：%s
            年份：%s
            关键词：%s
            摘要：%s
            """.formatted(
            paper.getTitle(),
            defaultText(paper.getAuthors(), "未填写"),
            defaultText(paper.getVenue(), "未填写"),
            paper.getYear() == null ? "未填写" : String.valueOf(paper.getYear()),
            defaultText(paper.getKeywords(), "未填写"),
            defaultText(paper.getAbstractText(), "未填写")
        ).trim();
    }

    private String sourceText(List<SourceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "无可用检索片段。";
        }
        StringBuilder builder = new StringBuilder();
        for (SourceResponse source : sources) {
            builder.append("- 《").append(source.title()).append("》第 ").append(source.page()).append(" 页：")
                .append(source.content()).append("\n");
        }
        return builder.toString().trim();
    }

    private String builtinSystemPrompt() {
        return """
            你是 Research Paper Agent 的论文精读 Agent。
            必须基于给定范围、文献题录和检索片段回答。
            如果提供了业务工具结果，可以用它回答文献库统计、解析状态和系统运营类问题。
            必须遵守用户消息中的“回答策略”和“输出契约”。
            不要在最终答案中复述“回答策略”“输出契约”等内部字段名。
            如果材料不足，明确说明“材料不足”，不要编造实验结果。
            用结构化中文 Markdown 输出，并尽量附上论文标题和来源页码。
            """;
    }

    private String builtinUserPromptTemplate() {
        return """
            回答范围：{{scope}}
            {{paper_metadata}}
            回答策略：{{answer_strategy}}
            输出契约：
            {{answer_contract}}
            历史对话：
            {{conversation_history}}
            业务工具结果：
            {{tool_context}}
            用户问题：{{question}}
            检索片段：
            {{sources}}
            """;
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private String normalizeCode(String value) {
        String normalized = compact(value, 64);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int candidate = value == null ? fallback : value;
        return Math.max(min, Math.min(max, candidate));
    }

    private String cleanup(String value) {
        return defaultText(value, "").replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
