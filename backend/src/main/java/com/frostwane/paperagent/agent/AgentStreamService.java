package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatResponse;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamEvent;
import com.frostwane.paperagent.user.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentStreamService {

    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    private final AgentOrchestratorService orchestratorService;
    private final AsyncTaskExecutor streamExecutor;

    public AgentStreamService(
        AgentOrchestratorService orchestratorService,
        @Qualifier("agentStreamExecutor") AsyncTaskExecutor streamExecutor
    ) {
        this.orchestratorService = orchestratorService;
        this.streamExecutor = streamExecutor;
    }

    public SseEmitter stream(ChatRequest request, User owner) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        emitter.onCompletion(() -> {
            closed.set(true);
            cancel(futureRef.get());
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            cancel(futureRef.get());
        });
        emitter.onError(ignored -> {
            closed.set(true);
            cancel(futureRef.get());
        });

        Future<?> future = streamExecutor.submit(() -> run(request, owner, emitter, closed));
        futureRef.set(future);
        return emitter;
    }

    private void run(ChatRequest request, User owner, SseEmitter emitter, AtomicBoolean closed) {
        try {
            send(emitter, closed, "started", "started", "已建立流式问答连接。", null, null);
            send(emitter, closed, "running", "running", "多 Agent 正在检索、生成并校验引用...", null, null);
            ChatResponse response = orchestratorService.chat(request, owner);
            send(emitter, closed, "final", "final", "回答已生成并保存。", response, null);
            send(emitter, closed, "done", "done", "流式问答完成。", null, null);
            complete(emitter, closed);
        } catch (Exception ex) {
            if (!closed.get()) {
                try {
                    send(emitter, closed, "error", "error", "流式问答失败。", null, sanitize(ex));
                } catch (Exception ignored) {
                    // The client may already be gone; the original error has been sent to Trace by the orchestrator.
                }
                complete(emitter, closed);
            }
        }
    }

    private void send(
        SseEmitter emitter,
        AtomicBoolean closed,
        String eventName,
        String phase,
        String message,
        ChatResponse response,
        String errorMessage
    ) throws IOException {
        if (closed.get()) {
            return;
        }
        emitter.send(SseEmitter.event()
            .name(eventName)
            .data(new ChatStreamEvent(phase, message, response, errorMessage)));
    }

    private void complete(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
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
}
