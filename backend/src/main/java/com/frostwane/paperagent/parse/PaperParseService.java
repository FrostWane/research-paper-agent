package com.frostwane.paperagent.parse;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PaperParseService(
        PaperService paperService,
        PaperRepository paperRepository,
        PaperChunkRepository chunkRepository,
        FileService fileService,
        PaperEmbeddingIndexer embeddingIndexer,
        ParseJobService parseJobService
    ) {
        this.paperService = paperService;
        this.paperRepository = paperRepository;
        this.chunkRepository = chunkRepository;
        this.fileService = fileService;
        this.embeddingIndexer = embeddingIndexer;
        this.parseJobService = parseJobService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ParseStatusResponse parse(Long paperId, User owner) {
        Instant started = Instant.now();
        Paper paper = paperService.requireOwnedPaper(paperId, owner.getId());
        PaperFile file = paper.getFile();
        if (file == null) {
            throw new BusinessException("该文献未关联 PDF 文件");
        }
        ParseJob job = parseJobService.start(owner, paper, file);
        int pageCount = file.getPageCount() == null ? 0 : file.getPageCount();
        int chunkCount = 0;

        paper.setProcessStatus(ProcessStatus.PARSING);
        paperRepository.saveAndFlush(paper);
        chunkRepository.deleteByPaperId(paper.getId());

        try {
            byte[] pdfBytes = readAll(fileService.openPdf(file));
            List<PaperChunk> chunks = extractChunks(paper, pdfBytes);
            chunkCount = chunks.size();
            paper.setProcessStatus(ProcessStatus.INDEXING);
            paperRepository.saveAndFlush(paper);
            List<PaperChunk> savedChunks = chunkRepository.saveAllAndFlush(chunks);
            embeddingIndexer.index(savedChunks);
            paper.setProcessStatus(ProcessStatus.INDEXED);
            paperRepository.save(paper);
            parseJobService.succeed(job, pageCount, chunkCount, elapsedMs(started));
            return new ParseStatusResponse(paper.getId(), ProcessStatus.INDEXED.name(), "PDF 已解析并写入向量索引，可用于来源片段检索", 100, chunks.size());
        } catch (Exception ex) {
            chunkRepository.deleteByPaperId(paper.getId());
            paper.setProcessStatus(ProcessStatus.FAILED);
            paperRepository.saveAndFlush(paper);
            String message = "PDF 解析失败：" + ex.getMessage();
            parseJobService.fail(job, pageCount, chunkCount, elapsedMs(started), message);
            throw new BusinessException(message);
        }
    }

    @Transactional
    public ParseStatusResponse unparse(Long paperId, User owner) {
        Paper paper = paperService.requireOwnedPaper(paperId, owner.getId());
        chunkRepository.deleteByPaperId(paper.getId());
        paper.setProcessStatus(ProcessStatus.PENDING);
        paperRepository.save(paper);
        return new ParseStatusResponse(paper.getId(), ProcessStatus.PENDING.name(), "已从知识库移除解析结果，PDF 文件仍保留", 0, 0);
    }

    private List<PaperChunk> extractChunks(Paper paper, byte[] pdfBytes) throws Exception {
        List<PaperChunk> chunks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
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
        return chunks;
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
}
