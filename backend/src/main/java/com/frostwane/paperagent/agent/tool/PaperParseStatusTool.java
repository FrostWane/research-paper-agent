package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.ProcessStatus;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import com.frostwane.paperagent.parse.ParseJob;
import com.frostwane.paperagent.parse.ParseJobRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
public class PaperParseStatusTool implements AgentTool {

    private final PaperChunkRepository chunkRepository;
    private final ParseJobRepository parseJobRepository;

    public PaperParseStatusTool(PaperChunkRepository chunkRepository, ParseJobRepository parseJobRepository) {
        this.chunkRepository = chunkRepository;
        this.parseJobRepository = parseJobRepository;
    }

    @Override
    public String name() {
        return "paper-parse-status";
    }

    @Override
    public String label() {
        return "单篇解析状态";
    }

    @Override
    public String description() {
        return "读取当前论文的 PDF、解析任务、知识片段、启用状态和向量化覆盖。";
    }

    @Override
    public String triggerDescription() {
        return "当单篇问答询问当前论文是否已解析、为何解析失败、是否可检索、chunk 或向量索引状态时触发。";
    }

    @Override
    public boolean supports(AgentPipelineContext context) {
        if (context == null || context.libraryScope() || context.paper() == null) {
            return false;
        }
        String text = normalize(context.question() + " " + context.searchQuery());
        boolean parseIntent = containsAny(text,
            "解析", "没解析", "无法解析", "入库", "入库详情", "可检索", "无法检索", "检索状态",
            "失败原因", "pdf 状态", "pdf状态", "pdf 页数", "pdf页数", "chunk", "chunks",
            "知识片段", "片段状态", "没有片段", "向量化", "向量索引", "embedding", "parse",
            "parse status", "parse failure", "indexed", "index status"
        );
        return parseIntent;
    }

    @Override
    public ToolExecutionOutput execute(AgentPipelineContext context) {
        if (context == null || context.paper() == null) {
            return new ToolExecutionOutput(
                "当前问题没有绑定具体论文，无法读取单篇解析状态。",
                "请在单篇阅读页或携带 paperId 的问答范围内询问解析、入库、chunk 或向量索引状态。"
            );
        }
        Paper paper = context.paper();
        Long paperId = paper.getId();
        Long ownerId = context.owner() == null ? null : context.owner().getId();
        long totalChunks = chunkRepository.countByPaperId(paperId);
        long enabledChunks = chunkRepository.countByPaperIdAndEnabledTrue(paperId);
        long embeddedChunks = chunkRepository.countByPaperIdAndEmbeddingIdIsNotNull(paperId);
        long enabledEmbeddedChunks = chunkRepository.countByPaperIdAndEnabledTrueAndEmbeddingIdIsNotNull(paperId);
        Optional<ParseJob> latestJob = ownerId == null
            ? Optional.empty()
            : parseJobRepository.findTopByOwnerIdAndPaperIdOrderByStartedAtDesc(ownerId, paperId);
        boolean retrievable = paper.getProcessStatus() == ProcessStatus.INDEXED && enabledEmbeddedChunks > 0;

        String summary = "当前论文《%s》的解析状态为 %s，%s；知识片段 %d 个，其中启用 %d 个、已向量化 %d 个。"
            .formatted(
                defaultText(paper.getTitle(), "未命名论文"),
                statusLabel(paper.getProcessStatus()),
                retrievable ? "可以参与检索" : "暂不可稳定参与检索",
                totalChunks,
                enabledChunks,
                enabledEmbeddedChunks
            );
        String details = """
            PDF：%s
            片段：总数 %d，启用 %d，停用 %d，已向量化 %d，启用且已向量化 %d。
            最近解析任务：%s
            检索就绪判断：%s
            """.formatted(
            fileLine(paper.getFile()),
            totalChunks,
            enabledChunks,
            Math.max(0, totalChunks - enabledChunks),
            embeddedChunks,
            enabledEmbeddedChunks,
            latestJob.map(this::jobLine).orElse("暂无解析任务记录。"),
            retrievable ? "论文状态为已解析，且存在启用并已向量化的片段。" : notReadyReason(paper, totalChunks, enabledChunks, enabledEmbeddedChunks)
        ).trim();
        return new ToolExecutionOutput(summary, details);
    }

    private String jobLine(ParseJob job) {
        StringBuilder builder = new StringBuilder();
        builder.append(statusText(job.getStatus()))
            .append("，页数 ").append(safeInt(job.getPageCount()))
            .append("，片段 ").append(safeInt(job.getChunkCount()))
            .append("，耗时 ").append(safeInt(job.getDurationMs())).append("ms")
            .append("，开始 ").append(formatTime(job.getStartedAt()));
        if (job.getFinishedAt() != null) {
            builder.append("，结束 ").append(formatTime(job.getFinishedAt()));
        }
        if (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()) {
            builder.append("，失败信息：").append(compact(job.getErrorMessage(), 220));
        }
        return builder.toString();
    }

    private String fileLine(PaperFile file) {
        if (file == null) {
            return "未绑定 PDF 文件。";
        }
        return "%s，页数 %s，大小 %s，SHA-256 %s。".formatted(
            defaultText(file.getOriginalName(), "未命名文件"),
            file.getPageCount() == null ? "未知" : file.getPageCount(),
            formatBytes(file.getSize()),
            compact(file.getSha256(), 16)
        );
    }

    private String notReadyReason(Paper paper, long totalChunks, long enabledChunks, long enabledEmbeddedChunks) {
        if (paper.getProcessStatus() != ProcessStatus.INDEXED) {
            return "论文状态仍是 " + statusLabel(paper.getProcessStatus()) + "。";
        }
        if (totalChunks == 0) {
            return "尚无知识片段。";
        }
        if (enabledChunks == 0) {
            return "知识片段已全部停用。";
        }
        if (enabledEmbeddedChunks == 0) {
            return "启用片段尚未写入向量索引。";
        }
        return "当前缺少可用于检索的启用向量片段。";
    }

    private String statusLabel(ProcessStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING -> "待解析";
            case PARSING -> "解析中";
            case INDEXING -> "索引中";
            case INDEXED -> "已解析";
            case FAILED -> "解析失败";
        };
    }

    private String statusText(String status) {
        if (status == null || status.isBlank()) {
            return "状态未知";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "QUEUED" -> "排队中";
            case "SUCCESS" -> "成功";
            case "FAILED" -> "失败";
            case "RUNNING" -> "运行中";
            default -> status;
        };
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(normalize(token))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{Punct}，。！？；：、（）【】《》]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int index = -1;
        do {
            value = value / 1024;
            index++;
        } while (value >= 1024 && index < units.length - 1);
        return "%.1f %s".formatted(value, units[index]);
    }

    private String formatTime(OffsetDateTime value) {
        return value == null ? "未知" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
