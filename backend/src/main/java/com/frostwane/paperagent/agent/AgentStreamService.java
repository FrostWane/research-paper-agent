package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamEvent;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamTaskResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentStreamService {

    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    private final AgentOrchestratorService orchestratorService;
    private final AsyncTaskExecutor streamExecutor;
    private final Map<String, StreamTask> tasks = new ConcurrentHashMap<>();

    public AgentStreamService(
        AgentOrchestratorService orchestratorService,
        @Qualifier("agentStreamExecutor") AsyncTaskExecutor streamExecutor
    ) {
        this.orchestratorService = orchestratorService;
        this.streamExecutor = streamExecutor;
    }

    public SseEmitter stream(ChatRequest request, User owner) {
        String taskId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        StreamTask task = new StreamTask(taskId, owner.getId(), request, emitter, closed, futureRef);
        tasks.put(taskId, task);

        emitter.onCompletion(() -> {
            closed.set(true);
            cancel(futureRef.get());
            tasks.remove(taskId);
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            cancel(futureRef.get());
            tasks.remove(taskId);
        });
        emitter.onError(ignored -> {
            closed.set(true);
            cancel(futureRef.get());
            tasks.remove(taskId);
        });

        Future<?> future = streamExecutor.submit(() -> run(task, owner));
        futureRef.set(future);
        return emitter;
    }

    public List<ChatStreamTaskResponse> activeTasks(User owner) {
        Long ownerId = owner.getId();
        return tasks.values().stream()
            .filter(task -> task.ownerId().equals(ownerId))
            .sorted(Comparator.comparing(StreamTask::startedAt).reversed())
            .map(StreamTask::response)
            .toList();
    }

    public ChatStreamTaskResponse cancel(String taskId, User owner) {
        StreamTask task = tasks.get(taskId);
        if (task == null || !task.ownerId().equals(owner.getId())) {
            throw new BusinessException("流式任务不存在或已结束");
        }
        task.cancelled().set(true);
        task.phase("cancelled");
        cancel(task.futureRef().get());
        try {
            send(task, "cancelled", "cancelled", "已取消本次流式问答任务。", null, null);
            send(task, "done", "done", "流式问答已停止。", null, null);
        } catch (Exception ignored) {
            // The browser may already have disconnected after pressing stop.
        }
        task.phase("cancelled");
        complete(task);
        tasks.remove(task.taskId());
        return task.response();
    }

    private void run(StreamTask task, User owner) {
        try {
            send(task, "started", "started", "已建立流式问答连接。", null, null);
            send(task, "running", "running", "多 Agent 正在检索、生成并校验引用...", null, null);
            if (task.closed().get() || task.cancelled().get()) {
                return;
            }
            ChatResponse response = orchestratorService.chat(task.request(), owner);
            if (!task.cancelled().get()) {
                send(task, "final", "final", "回答已生成并保存。", response, null);
                send(task, "done", "done", "流式问答完成。", null, null);
                complete(task);
            }
        } catch (Exception ex) {
            if (!task.closed().get() && !task.cancelled().get()) {
                try {
                    send(task, "error", "error", "流式问答失败。", null, sanitize(ex));
                } catch (Exception ignored) {
                    // The client may already be gone; the original error has been sent to Trace by the orchestrator.
                }
                complete(task);
            }
        } finally {
            tasks.remove(task.taskId());
        }
    }

    private void send(
        StreamTask task,
        String eventName,
        String phase,
        String message,
        ChatResponse response,
        String errorMessage
    ) throws IOException {
        if (task.closed().get()) {
            return;
        }
        task.phase(phase);
        task.emitter().send(SseEmitter.event()
            .name(eventName)
            .data(new ChatStreamEvent(task.taskId(), phase, message, response, errorMessage, task.cancelled().get())));
    }

    private void complete(StreamTask task) {
        if (task.closed().compareAndSet(false, true)) {
            task.emitter().complete();
        }
    }

    private void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private String sanitize(Exception ex) {
        String message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        String sanitized = message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private static final class StreamTask {
        private final String taskId;
        private final Long ownerId;
        private final ChatRequest request;
        private final SseEmitter emitter;
        private final AtomicBoolean closed;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<Future<?>> futureRef;
        private final OffsetDateTime startedAt = OffsetDateTime.now();
        private volatile OffsetDateTime updatedAt = startedAt;
        private volatile String phase = "queued";

        private StreamTask(
            String taskId,
            Long ownerId,
            ChatRequest request,
            SseEmitter emitter,
            AtomicBoolean closed,
            AtomicReference<Future<?>> futureRef
        ) {
            this.taskId = taskId;
            this.ownerId = ownerId;
            this.request = request;
            this.emitter = emitter;
            this.closed = closed;
            this.futureRef = futureRef;
        }

        private String taskId() {
            return taskId;
        }

        private Long ownerId() {
            return ownerId;
        }

        private ChatRequest request() {
            return request;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private AtomicBoolean closed() {
            return closed;
        }

        private AtomicBoolean cancelled() {
            return cancelled;
        }

        private AtomicReference<Future<?>> futureRef() {
            return futureRef;
        }

        private OffsetDateTime startedAt() {
            return startedAt;
        }

        private void phase(String phase) {
            this.phase = phase;
            this.updatedAt = OffsetDateTime.now();
        }

        private ChatStreamTaskResponse response() {
            return new ChatStreamTaskResponse(
                taskId,
                phase,
                request.question(),
                request.paperId(),
                request.sessionId(),
                cancelled.get(),
                startedAt,
                updatedAt
            );
        }
    }
}
