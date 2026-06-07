package com.frostwane.paperagent.parse;

import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ParseJobService {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final List<String> ACTIVE_STATUSES = List.of(STATUS_QUEUED, STATUS_RUNNING);

    private final ParseJobRepository parseJobRepository;

    public ParseJobService(ParseJobRepository parseJobRepository) {
        this.parseJobRepository = parseJobRepository;
    }

    public Optional<ParseJob> activeJob(Long ownerId, Long paperId) {
        return parseJobRepository.findTopByOwnerIdAndPaperIdAndStatusInOrderByStartedAtDesc(ownerId, paperId, ACTIVE_STATUSES);
    }

    public ParseJob enqueue(User owner, Paper paper, PaperFile file) {
        ParseJob job = new ParseJob();
        job.setOwner(owner);
        job.setPaper(paper);
        job.setFile(file);
        job.setPaperTitle(compact(paper.getTitle(), 512));
        job.setFileName(compact(file.getOriginalName(), 512));
        job.setFileSize(file.getSize());
        job.setStatus(STATUS_QUEUED);
        job.setPageCount(file.getPageCount() == null ? 0 : file.getPageCount());
        return parseJobRepository.saveAndFlush(job);
    }

    public void markRunning(ParseJob job) {
        job.setStatus(STATUS_RUNNING);
        parseJobRepository.save(job);
    }

    public void succeed(ParseJob job, int pageCount, int chunkCount, int durationMs, String nodeSpansJson) {
        finish(job, STATUS_SUCCESS, pageCount, chunkCount, durationMs, nodeSpansJson, null);
    }

    public void fail(ParseJob job, int pageCount, int chunkCount, int durationMs, String nodeSpansJson, String errorMessage) {
        finish(job, STATUS_FAILED, pageCount, chunkCount, durationMs, nodeSpansJson, sanitizeError(errorMessage));
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
