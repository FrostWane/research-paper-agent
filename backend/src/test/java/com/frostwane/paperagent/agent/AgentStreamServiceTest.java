package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.dto.AgentDtos.ChatStreamTaskResponse;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgentStreamServiceTest {

    @Test
    void activeTasksExposeTaskIdAndCancelRemovesTask() {
        QueuedAsyncTaskExecutor executor = new QueuedAsyncTaskExecutor();
        AgentStreamService service = new AgentStreamService(mock(AgentOrchestratorService.class), executor);
        User owner = user(11L);

        service.stream(new ChatRequest(null, null, "请总结一下", true), owner);

        List<ChatStreamTaskResponse> activeTasks = service.activeTasks(owner);
        assertThat(activeTasks).hasSize(1);
        assertThat(activeTasks.get(0).phase()).isEqualTo("queued");
        assertThat(activeTasks.get(0).question()).isEqualTo("请总结一下");

        ChatStreamTaskResponse cancelled = service.cancel(activeTasks.get(0).taskId(), owner);

        assertThat(cancelled.cancelled()).isTrue();
        assertThat(cancelled.phase()).isEqualTo("cancelled");
        assertThat(service.activeTasks(owner)).isEmpty();
        assertThat(executor.futures()).hasSize(1);
        assertThat(executor.futures().get(0).isCancelled()).isTrue();
    }

    @Test
    void cancelRejectsTaskOwnedByAnotherUser() {
        QueuedAsyncTaskExecutor executor = new QueuedAsyncTaskExecutor();
        AgentStreamService service = new AgentStreamService(mock(AgentOrchestratorService.class), executor);
        User owner = user(12L);
        User other = user(13L);

        service.stream(new ChatRequest(null, null, "请总结一下", true), owner);
        String taskId = service.activeTasks(owner).get(0).taskId();

        assertThatThrownBy(() -> service.cancel(taskId, other))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("流式任务不存在");
        assertThat(service.activeTasks(owner)).hasSize(1);
    }

    private User user(Long id) {
        try {
            User user = new User();
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class QueuedAsyncTaskExecutor implements AsyncTaskExecutor {
        private final List<FutureTask<?>> futures = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            submit(task);
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            submit(task);
        }

        @Override
        public Future<?> submit(Runnable task) {
            FutureTask<?> future = new FutureTask<>(task, null);
            futures.add(future);
            return future;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            FutureTask<T> future = new FutureTask<>(task);
            futures.add(future);
            return future;
        }

        private List<FutureTask<?>> futures() {
            return futures;
        }
    }
}
