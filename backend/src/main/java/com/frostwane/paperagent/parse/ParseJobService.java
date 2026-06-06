package com.frostwane.paperagent.parse;

import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ParseJobService {

    private final ParseJobRepository parseJobRepository;

    public ParseJobService(ParseJobRepository parseJobRepository) {
        this.parseJobRepository = parseJobRepository;
    }

    public ParseJob start(User owner, Paper paper, PaperFile file) {
        ParseJob job = new ParseJob();
        job.setOwner(owner);
        job.setPaper(paper);
        job.setFile(file);
        job.setPaperTitle(compact(paper.getTitle(), 512));
        job.setFileName(compact(file.getOriginalName(), 512));
        job.setFileSize(file.getSize());
        job.setStatus("RUNNING");
        job.setPageCount(file.getPageCount() == null ? 0 : file.getPageCount());
        return parseJobRepository.save(job);
    }

    public void succeed(ParseJob job, int pageCount, int chunkCount, int durationMs, String nodeSpansJson) {
        finish(job, "SUCCESS", pageCount, chunkCount, durationMs, nodeSpansJson, null);
    }

    public void fail(ParseJob job, int pageCount, int chunkCount, int durationMs, String nodeSpansJson, String errorMessage) {
        finish(job, "FAILED", pageCount, chunkCount, durationMs, nodeSpansJson, sanitizeError(errorMessage));
    }

    private void finish(ParseJob job, String status, int pageCount, int chunkCount, int durationMs, String nodeSpansJson, String errorMessage) {
        job.setStatus(status);
        job.setPageCount(Math.max(0, pageCount));
        job.setChunkCount(Math.max(0, chunkCount));
        job.setDurationMs(Math.max(0, durationMs));
        job.setNodeSpansJson(nodeSpansJson == null || nodeSpansJson.isBlank() ? "[]" : nodeSpansJson);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(OffsetDateTime.now());
        parseJobRepository.save(job);
    }

    private String sanitizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 600 ? sanitized.substring(0, 600) : sanitized;
    }

    private String compact(String value, int maxLength) {
        String sanitized = value == null || value.isBlank() ? "unknown" : value.trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
