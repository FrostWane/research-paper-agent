package com.frostwane.paperagent.agent.limit;

import com.frostwane.paperagent.agent.settings.RagSettingsService;
import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import com.frostwane.paperagent.common.RateLimitException;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class AgentRateLimiterService {

    private static final long WINDOW_MS = 60_000L;

    private final RagSettingsService ragSettingsService;
    private final Object monitor = new Object();
    private final Map<Long, Integer> activeByUser = new HashMap<>();
    private final Map<Long, ArrayDeque<Long>> recentRequestsByUser = new HashMap<>();
    private int activeGlobal;

    public AgentRateLimiterService(RagSettingsService ragSettingsService) {
        this.ragSettingsService = ragSettingsService;
    }

    public AgentRateLimitPermit acquire(User owner) {
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        if (!settings.chatRateLimitEnabled()) {
            return AgentRateLimitPermit.noop();
        }
        Long userId = owner.getId();
        long now = System.currentTimeMillis();

        synchronized (monitor) {
            pruneAll(now);
            ArrayDeque<Long> recentRequests = recentRequestsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
            if (activeGlobal >= settings.chatRateLimitGlobalConcurrency()) {
                throw new RateLimitException("当前 Agent 问答并发已满，请稍后再试。");
            }
            if (activeByUser.getOrDefault(userId, 0) >= settings.chatRateLimitUserConcurrency()) {
                throw new RateLimitException("当前账号已有问答正在运行，请等待上一轮完成后再试。");
            }
            if (recentRequests.size() >= settings.chatRateLimitUserPerMinute()) {
                throw new RateLimitException("当前账号问答请求过于频繁，请稍后再试。");
            }

            activeGlobal++;
            activeByUser.merge(userId, 1, Integer::sum);
            recentRequests.addLast(now);
            return AgentRateLimitPermit.acquired(this, userId);
        }
    }

    void release(Long userId) {
        synchronized (monitor) {
            activeGlobal = Math.max(0, activeGlobal - 1);
            Integer current = activeByUser.get(userId);
            if (current == null || current <= 1) {
                activeByUser.remove(userId);
            } else {
                activeByUser.put(userId, current - 1);
            }
        }
    }

    public AgentRateLimitStatus status() {
        RagSettingsSnapshot settings = ragSettingsService.snapshot();
        synchronized (monitor) {
            pruneAll(System.currentTimeMillis());
            int recentRequests = recentRequestsByUser.values().stream().mapToInt(ArrayDeque::size).sum();
            return new AgentRateLimitStatus(
                settings.chatRateLimitEnabled(),
                activeGlobal,
                activeByUser.size(),
                recentRequests,
                settings.chatRateLimitGlobalConcurrency(),
                settings.chatRateLimitUserConcurrency(),
                settings.chatRateLimitUserPerMinute()
            );
        }
    }

    private void pruneAll(long now) {
        Iterator<Map.Entry<Long, ArrayDeque<Long>>> iterator = recentRequestsByUser.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ArrayDeque<Long>> entry = iterator.next();
            ArrayDeque<Long> queue = entry.getValue();
            while (!queue.isEmpty() && now - queue.peekFirst() >= WINDOW_MS) {
                queue.removeFirst();
            }
            if (queue.isEmpty() && !activeByUser.containsKey(entry.getKey())) {
                iterator.remove();
            }
        }
    }
}
