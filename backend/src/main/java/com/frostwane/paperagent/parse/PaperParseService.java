package com.frostwane.paperagent.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.embedding.PaperEmbeddingIndexer;
import com.frostwane.paperagent.file.FileService;
import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperService;
import com.frostwane.paperagent.paper.PaperRepository;
import com.frostwane.paperagent.paper.ProcessStatus;
import com.frostwane.paperagent.paper.dto.PaperDtos.ParseStatusResponse;
import com.frostwane.paperagent.user.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaperParseService {

    private static final int CHUNK_TARGET_CHARS = 1400;
    private static final int CHUNK_OVERLAP_CHARS = 180;

    private final PaperService paperService;
    private final PaperRepository paperRepository;
    private final PaperChunkRepository chunkRepository;
    private final FileService fileService;
    private final PaperEmbeddingIndexer embeddingIndexer;
    private final ParseJobService parseJobService;
    private final ParseJobRepository parseJobRepository;
    private final ThreadPoolTaskExecutor parseTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PaperParseService(
        PaperService paperService,
        PaperRepository paperRepository,
        PaperChunkRepository chunkRepository,
        FileService fileService,
        PaperEmbeddingIndexer embeddingIndexer,
        ParseJobService parseJobService,
        ParseJobRepository parseJobRepository,
        @Qualifier("parseTaskExecutor") ThreadPoolTaskExecutor parseTaskExecutor,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        this.paperService = paperService;
        this.paperRepository = paperRepository;
        this.chunkRepository = chunkRepository;
        this.fileService = fileService;
        this.embeddingIndexer = embeddingIndexer;
        this.parseJobService = parseJobService;
        this.parseJobRepository = parseJobRepository;
        this.parseTaskExecutor = parseTaskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ParseStatusResponse parse(Long paperId, User owner) {
        Long ownerId = owner.getId();
        Paper paper = lockOwnedPaper(paperId, ownerId);
        PaperFile file = paper.getFile();
        if (file == null) {
            throw new BusinessException("该文献未关联 PDF 文件");
        }
        long existingChunks = chunkRepository.countByPaperId(paper.getId());
        ParseJob activeJob = parseJobService.activeJob(ownerId, paper.getId()).orElse(null);
        if (activeJob != null) {
            paper.setProcessStatus(ProcessStatus.PARSING);
            paperRepository.save(paper);
            return queuedResponse(paper.getId(), activeJob, existingChunks);
        }
        ParseJob job = parseJobService.enqueue(owner, paper, file);
        paper.setProcessStatus(ProcessStatus.PARSING);
        paperRepository.save(paper);
        scheduleAfterCommit(job.getId(), ownerId, paper.getId());
        return new ParseStatusResponse(
            paper.getId(),
            ProcessStatus.PARSING.name(),
            "解析任务已提交，后台队列将依次读取 PDF、切块并写入向量索引",
            10,
            existingChunks
        );
    }

    @Transactional
    public ParseStatusResponse unparse(Long paperId, User owner) {
        Long ownerId = owner.getId();
        Paper paper = lockOwnedPaper(paperId, ownerId);
        if (parseJobService.activeJob(ownerId, paper.getId()).isPresent()) {
            throw new BusinessException("解析任务正在排队或运行，请完成后再移除解析结果");
        }
        chunkRepository.deleteByPaperId(paper.getId());
        paper.setProcessStatus(ProcessStatus.PENDING);
        paperRepository.save(paper);
        return new ParseStatusResponse(paper.getId(), ProcessStatus.PENDING.name(), "已从知识库移除解析结果，PDF 文件仍保留", 0, 0);
    }

    private ParseStatusResponse queuedResponse(Long paperId, ParseJob job, long chunkCount) {
        boolean running = ParseJobService.STATUS_RUNNING.equals(job.getStatus());
        return new ParseStatusResponse(
            paperId,
            ProcessStatus.PARSING.name(),
            running ? "已有解析任务正在运行，请稍后刷新状态" : "已有解析任务在后台排队，请稍后刷新状态",
            running ? 20 : 10,
            chunkCount
        );
    }

    private Paper lockOwnedPaper(Long paperId, Long ownerId) {
        return paperRepository.lockByIdAndOwnerId(paperId, ownerId)
            .orElseThrow(() -> new BusinessException("文献不存在或无权访问"));
    }

    private void scheduleAfterCommit(Long jobId, Long ownerId, Long paperId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitParseJob(jobId, ownerId, paperId);
                }
            });
            return;
        }
        submitParseJob(jobId, ownerId, paperId);
    }

    private void submitParseJob(Long jobId, Long ownerId, Long paperId) {
        try {
            parseTaskExecutor.execute(() -> runParseJob(jobId, ownerId, paperId));
        } catch (TaskRejectedException ex) {
            markDispatchFailed(jobId, ownerId, paperId, ex);
        }
    }

    private void runParseJob(Long jobId, Long ownerId, Long paperId) {
        try {
            transactionTemplate.executeWithoutResult(status -> markRunning(jobId, ownerId, paperId));
            transactionTemplate.executeWithoutResult(status -> executeParseJob(jobId, ownerId, paperId));
        } catch (RuntimeException ex) {
            markDispatchFailed(jobId, ownerId, paperId, ex);
        }
    }

    private void markRunning(Long jobId, Long ownerId, Long paperId) {
        ParseJob job = parseJobRepository.findById(jobId)
            .orElseThrow(() -> new BusinessException("解析任务不存在"));
        Paper paper = paperService.requireOwnedPaper(paperId, ownerId);
        parseJobService.markRunning(job);
        paper.setProcessStatus(ProcessStatus.PARSING);
        paperRepository.save(paper);
    }

    private void executeParseJob(Long jobId, Long ownerId, Long paperId) {
        Instant started = Instant.now();
        ParseJob job = parseJobRepository.findById(jobId)
            .orElseThrow(() -> new BusinessException("解析任务不存在"));
        Paper paper = paperService.requireOwnedPaper(paperId, ownerId);
        PaperFile file = paper.getFile();
        if (file == null) {
            failJob(job, paper, 0, 0, elapsedMs(started), "[]", "该文献未关联 PDF 文件");
            return;
        }
        int pageCount = file.getPageCount() == null ? 0 : file.getPageCount();
        int chunkCount = 0;
        IngestionTrace trace = new IngestionTrace();

        try {
            trace.run("prepare", "准备", 10, () -> {
                paper.setProcessStatus(ProcessStatus.PARSING);
                paperRepository.saveAndFlush(paper);
                chunkRepository.deleteByPaperId(paper.getId());
            });
            byte[] pdfBytes = trace.call("fetch-pdf", "读取PDF", 20, () -> readAll(fileService.openPdf(file)));
            ExtractionResult extraction = trace.call("parse-pdf", "抽取文本", 30, () -> extractChunks(paper, pdfBytes));
            pageCount = extraction.pageCount();
            List<PaperChunk> chunks = extraction.chunks();
            chunkCount = chunks.size();
            List<PaperChunk> savedChunks = trace.call("persist-chunks", "写入片段", 40, () -> {
                paper.setProcessStatus(ProcessStatus.INDEXING);
                paperRepository.saveAndFlush(paper);
                return chunkRepository.saveAllAndFlush(chunks);
            });
            trace.run("index-embeddings", "向量索引", 50, () -> embeddingIndexer.index(savedChunks));
            trace.run("finalize", "完成", 60, () -> {
                paper.setProcessStatus(ProcessStatus.INDEXED);
                paperRepository.save(paper);
            });
            parseJobService.succeed(job, pageCount, chunkCount, elapsedMs(started), trace.toJson());
        } catch (Exception ex) {
            String message = "PDF 解析失败：" + ex.getMessage();
            failJob(job, paper, pageCount, chunkCount, elapsedMs(started), trace.toJson(), message);
        }
    }

    private void markDispatchFailed(Long jobId, Long ownerId, Long paperId, Exception exception) {
        transactionTemplate.executeWithoutResult(status -> {
            ParseJob job = parseJobRepository.findById(jobId).orElse(null);
            Paper paper = null;
            try {
                paper = paperService.requireOwnedPaper(paperId, ownerId);
            } catch (RuntimeException ignored) {
                // 文献可能已被删除，此时只标记任务失败。
            }
            String message = "解析任务提交失败：" + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            failJob(job, paper, 0, 0, 0, "[]", message);
        });
    }

    private void failJob(ParseJob job, Paper paper, int pageCount, int chunkCount, int durationMs, String nodeSpansJson, String message) {
        if (paper != null) {
            chunkRepository.deleteByPaperId(paper.getId());
            paper.setProcessStatus(ProcessStatus.FAILED);
            paperRepository.saveAndFlush(paper);
        }
        if (job != null) {
            parseJobService.fail(job, pageCount, chunkCount, durationMs, nodeSpansJson, message);
        }
    }

    private ExtractionResult extractChunks(Paper paper, byte[] pdfBytes) throws Exception {
        List<PaperChunk> chunks = new ArrayList<>();
        int pageCount;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (text.isBlank()) {
                    continue;
                }
                List<String> pageChunks = splitText(text);
                for (int index = 0; index < pageChunks.size(); index++) {
                    PaperChunk chunk = new PaperChunk();
                    chunk.setPaper(paper);
                    chunk.setPageNumber(page);
                    chunk.setChunkIndex(index);
                    chunk.setContent(pageChunks.get(index));
                    chunks.add(chunk);
                }
            }
        }
        if (chunks.isEmpty()) {
            throw new BusinessException("未从 PDF 中提取到可用文本");
        }
        return new ExtractionResult(chunks, pageCount);
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_TARGET_CHARS);
            if (end < text.length()) {
                int sentenceBoundary = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('.', end));
                if (sentenceBoundary > start + CHUNK_TARGET_CHARS / 2) {
                    end = sentenceBoundary + 1;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP_CHARS);
        }
        return chunks;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private byte[] readAll(InputStream inputStream) throws Exception {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private record ExtractionResult(List<PaperChunk> chunks, int pageCount) {
    }

    private class IngestionTrace {
        private final List<NodeSpan> spans = new ArrayList<>();

        private <T> T call(String name, String label, int order, ThrowingSupplier<T> supplier) throws Exception {
            Instant nodeStarted = Instant.now();
            Exception failure = null;
            try {
                return supplier.get();
            } catch (Exception ex) {
                failure = ex;
                throw ex;
            } finally {
                spans.add(new NodeSpan(
                    "INGESTION",
                    name,
                    label,
                    order,
                    failure == null ? "SUCCESS" : "FAILED",
                    elapsedMs(nodeStarted),
                    failure == null ? null : sanitizeError(failure)
                ));
            }
        }

        private void run(String name, String label, int order, ThrowingRunnable runnable) throws Exception {
            call(name, label, order, () -> {
                runnable.run();
                return null;
            });
        }

        private String toJson() {
            return PaperParseService.this.toJson(spans);
        }
    }

    private String sanitizeError(Exception exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getClass().getSimpleName() + ": " + (exception.getMessage() == null ? "" : exception.getMessage());
        String sanitized = message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 600 ? sanitized.substring(0, 600) : sanitized;
    }

    private record NodeSpan(
        String type,
        String name,
        String label,
        int order,
        String status,
        int durationMs,
        String errorMessage
    ) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
