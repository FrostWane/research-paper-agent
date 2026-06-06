package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.ChatRecordRepository;
import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.file.PaperFileRepository;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperRepository;
import com.frostwane.paperagent.paper.ProcessStatus;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import com.frostwane.paperagent.parse.ParseJobRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LibraryStatsTool implements AgentTool {

    private final PaperRepository paperRepository;
    private final PaperChunkRepository chunkRepository;
    private final PaperFileRepository fileRepository;
    private final ParseJobRepository parseJobRepository;
    private final ChatRecordRepository chatRecordRepository;

    public LibraryStatsTool(
        PaperRepository paperRepository,
        PaperChunkRepository chunkRepository,
        PaperFileRepository fileRepository,
        ParseJobRepository parseJobRepository,
        ChatRecordRepository chatRecordRepository
    ) {
        this.paperRepository = paperRepository;
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
        this.parseJobRepository = parseJobRepository;
        this.chatRecordRepository = chatRecordRepository;
    }

    @Override
    public String name() {
        return "library-stats";
    }

    @Override
    public String label() {
        return "文献库统计";
    }

    @Override
    public String description() {
        return "读取当前用户文献、解析、知识片段、文件和问答历史统计。";
    }

    @Override
    public String triggerDescription() {
        return "当问题包含统计、数量、解析状态、PDF 文件、知识片段或问答历史等运营类意图时触发；支持全库和单篇范围。";
    }

    @Override
    public boolean supports(AgentPipelineContext context) {
        String text = normalize(context.question() + " " + context.searchQuery());
        boolean metricIntent = containsAny(text, "统计", "数量", "多少", "几篇", "概览", "状态", "情况", "解析率", "已解析", "待解析");
        boolean operationalSubject = containsAny(text, "pdf", "文件", "片段", "问答", "历史", "解析", "索引", "入库");
        boolean librarySubject = containsAny(text, "文献库", "知识库", "当前", "文献", "论文", "library", "paper", "document");
        boolean scopedLibraryMetric = context.libraryScope() && metricIntent && containsAny(text, "篇", "文献", "论文", "pdf", "文件", "片段", "问答", "解析", "索引");
        boolean scopedPaperMetric = !context.libraryScope() && metricIntent && operationalSubject;
        boolean englishIntent = containsAny(text, "stats", "status", "count", "overview", "dashboard");
        return scopedLibraryMetric || scopedPaperMetric || (metricIntent && operationalSubject && librarySubject) || englishIntent;
    }

    @Override
    public ToolExecutionOutput execute(AgentPipelineContext context) {
        Long ownerId = context.owner().getId();
        if (!context.libraryScope() && context.paper() != null) {
            return paperStats(context.paper());
        }

        long totalPapers = paperRepository.countByOwnerId(ownerId);
        long indexedPapers = paperRepository.countByOwnerIdAndProcessStatus(ownerId, ProcessStatus.INDEXED);
        long pendingPapers = paperRepository.countByOwnerIdAndProcessStatus(ownerId, ProcessStatus.PENDING);
        long parsingPapers = paperRepository.countByOwnerIdAndProcessStatus(ownerId, ProcessStatus.PARSING);
        long indexingPapers = paperRepository.countByOwnerIdAndProcessStatus(ownerId, ProcessStatus.INDEXING);
        long failedPapers = paperRepository.countByOwnerIdAndProcessStatus(ownerId, ProcessStatus.FAILED);
        long chunks = chunkRepository.countByOwnerId(ownerId);
        long files = fileRepository.countByOwnerId(ownerId);
        long storageBytes = fileRepository.sumSizeByOwnerId(ownerId);
        long totalChats = chatRecordRepository.countByOwnerId(ownerId);
        long libraryChats = chatRecordRepository.countByOwnerIdAndPaperIsNull(ownerId);
        long parseJobs = parseJobRepository.countByOwnerId(ownerId);
        long failedParseJobs = parseJobRepository.countByOwnerIdAndStatus(ownerId, "FAILED");

        String summary = "当前文献库共有 %d 篇文献，%d 篇已解析，%d 个知识片段，%d 个 PDF 文件，累计 %d 次问答。"
            .formatted(totalPapers, indexedPapers, chunks, files, totalChats);
        String details = """
            解析状态：已解析 %d，待解析 %d，解析中 %d，索引中 %d，失败 %d。
            存储：PDF 文件 %d 个，约 %s。
            问答：全库问答 %d 次，总问答 %d 次。
            入库任务：共 %d 次，失败 %d 次。
            """.formatted(
            indexedPapers,
            pendingPapers,
            parsingPapers,
            indexingPapers,
            failedPapers,
            files,
            formatBytes(storageBytes),
            libraryChats,
            totalChats,
            parseJobs,
            failedParseJobs
        ).trim();
        return new ToolExecutionOutput(summary, details);
    }

    private ToolExecutionOutput paperStats(Paper paper) {
        long chunks = chunkRepository.countByPaperId(paper.getId());
        PaperFile file = paper.getFile();
        String summary = "当前论文《%s》的解析状态为 %s，已有 %d 个知识片段。"
            .formatted(paper.getTitle(), paper.getProcessStatus(), chunks);
        String details = """
            题录：作者 %s；年份 %s；关键词 %s。
            PDF：%s；页数 %s；大小 %s。
            摘要状态：%s。
            """.formatted(
            defaultText(paper.getAuthors(), "未填写"),
            paper.getYear() == null ? "未填写" : paper.getYear(),
            defaultText(paper.getKeywords(), "未填写"),
            file == null ? "未绑定文件" : file.getOriginalName(),
            file == null || file.getPageCount() == null ? "未知" : file.getPageCount(),
            file == null ? "-" : formatBytes(file.getSize()),
            paper.getAbstractText() == null || paper.getAbstractText().isBlank() ? "未填写摘要" : "已填写摘要"
        ).trim();
        return new ToolExecutionOutput(summary, details);
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

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
