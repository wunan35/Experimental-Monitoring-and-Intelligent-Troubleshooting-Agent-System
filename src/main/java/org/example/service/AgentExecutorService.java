package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent并发控制服务
 * 使用信号量和线程池限制并发Agent执行数量
 */
@Service
public class AgentExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutorService.class);

    @Value("${agent.concurrency.max-concurrent:10}")
    private int maxConcurrent;

    @Value("${agent.concurrency.queue-capacity:100}")
    private int queueCapacity;

    @Value("${agent.concurrency.timeout-seconds:300}")
    private int timeoutSeconds;

    private Semaphore semaphore;
    private ExecutorService executorService;
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final AtomicInteger totalExecuted = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        this.semaphore = new Semaphore(maxConcurrent);

        // 创建有界队列的线程池
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.executorService = new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                60L, TimeUnit.SECONDS,
                workQueue,
                new AgentThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("Agent并发控制服务初始化完成: maxConcurrent={}, queueCapacity={}, timeout={}s",
                maxConcurrent, queueCapacity, timeoutSeconds);
    }

    /**
     * 提交Agent执行任务
     *
     * @param task 任务
     * @param <T>  返回类型
     * @return 执行结果
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        queuedCount.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 尝试获取信号量
                if (!semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                    totalRejected.incrementAndGet();
                    throw new RejectedExecutionException("Agent执行队列已满，请稍后重试");
                }

                activeCount.incrementAndGet();
                queuedCount.decrementAndGet();

                try {
                    logger.debug("Agent任务开始执行, 当前活跃数: {}", activeCount.get());
                    T result = task.call();
                    totalExecuted.incrementAndGet();
                    return result;

                } finally {
                    activeCount.decrementAndGet();
                    semaphore.release();
                    logger.debug("Agent任务执行完成, 当前活跃数: {}", activeCount.get());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Agent执行被中断", e);
            } catch (Exception e) {
                logger.error("Agent执行失败", e);
                throw new RuntimeException("Agent执行失败: " + e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * 提交Agent执行任务（带超时）
     *
     * @param task         任务
     * @param timeoutValue 超时时间
     * @param unit         时间单位
     * @param <T>          返回类型
     * @return 执行结果
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, long timeoutValue, TimeUnit unit) {
        return submit(task).orTimeout(timeoutValue, unit);
    }

    /**
     * 尝试立即执行（不排队）
     *
     * @param task 任务
     * @param <T>  返回类型
     * @return 执行结果
     */
    public <T> CompletableFuture<T> tryExecuteImmediately(Callable<T> task) {
        if (!semaphore.tryAcquire()) {
            totalRejected.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("无可用的Agent执行槽位"));
        }

        return CompletableFuture.supplyAsync(() -> {
            activeCount.incrementAndGet();
            try {
                logger.debug("Agent任务立即执行, 当前活跃数: {}", activeCount.get());
                T result = task.call();
                totalExecuted.incrementAndGet();
                return result;
            } catch (Exception e) {
                logger.error("Agent立即执行失败", e);
                throw new RuntimeException("Agent执行失败: " + e.getMessage(), e);
            } finally {
                activeCount.decrementAndGet();
                semaphore.release();
            }
        }, executorService);
    }

    /**
     * 获取当前状态
     */
    public AgentExecutionStatus getStatus() {
        return new AgentExecutionStatus(
                maxConcurrent,
                activeCount.get(),
                queuedCount.get(),
                semaphore.availablePermits(),
                totalExecuted.get(),
                totalRejected.get()
        );
    }

    /**
     * 检查是否有可用槽位
     */
    public boolean hasAvailableSlots() {
        return semaphore.availablePermits() > 0;
    }

    /**
     * 等待所有任务完成
     */
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(timeout, unit)) {
            logger.warn("部分Agent任务未能在指定时间内完成");
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("关闭Agent执行服务...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                logger.warn("强制关闭Agent执行服务");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Agent执行服务已关闭");
    }

    /**
     * Agent执行状态
     */
    public record AgentExecutionStatus(
            int maxConcurrent,
            int activeCount,
            int queuedCount,
            int availablePermits,
            int totalExecuted,
            int totalRejected
    ) {
        public double getUtilizationRate() {
            return maxConcurrent > 0 ? (double) activeCount / maxConcurrent : 0;
        }
    }

    /**
     * 自定义线程工厂
     */
    private static class AgentThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "agent-executor-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
