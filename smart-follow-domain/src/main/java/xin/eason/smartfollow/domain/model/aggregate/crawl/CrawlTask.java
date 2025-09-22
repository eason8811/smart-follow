package xin.eason.smartfollow.domain.model.aggregate.crawl;

import lombok.*;
import xin.eason.smartfollow.types.enums.Exchange;
import xin.eason.smartfollow.types.enums.TaskStatus;

import java.time.Instant;

/**
 * 爬取任务聚合 (意图级/任务级去重)
 * <p>
 * <ul>
 *   <li>在同一时间窗口内, 针对同一交易所, 同一 API, 同一规范化参数, 仅创建并执行一次任务 (基于 exchange+apiName+paramsHash+windowKey 唯一键) </li>
 *   <li>记录任务执行进度 (分页 nextPage/totalPage) </li>
 *   <li>提供锁与过期 TTL, 避免单次执行时间过长导致的重入</li>
 * </ul>
 *
 * @author Eason
 * @version 1.0.0
 * @since 2025-09-07
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlTask {
    // =========================== 不变字段, 构造后不改 ===========================


    /**
     * 数据库主键ID
     */
    private Long id;
    /**
     * 目标交易所 (OKX/BINANCE)
     */
    private Exchange exchange;
    /**
     * API 名称 (如 COPYTRADING_PUBLIC_LEAD_TRADERS)
     */
    private String apiName;
    /**
     * 规范化参数哈希 (不含时变项) , 用于唯一键组成部分之一
     */
    private String paramsHash;
    /**
     * 规范化参数 JSON 字符串 (limit/sortType/state...)
     */
    private String paramsJson;
    /**
     * 窗口键 (如 dataVer=20231129213200) , 用于唯一键组成部分之一
     */
    private String windowKey;
    /**
     * 总页数
     */
    private Integer totalPage;
    // =========================== 可变字段, 通过领域方法修改 ===========================
    /**
     * 当前 Task 下一个准备要处理的页码 (从1开始) ；默认值: 1
     */
    @Builder.Default
    private Integer nextPage = 1;
    /**
     * 任务状态；默认值: PENDING
     */
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
    /**
     * 重试次数；默认值: 0
     */
    @Builder.Default
    private Integer attempts = 0;
    /**
     * 最后一次错误信息
     */
    private String lastError;
    // =========================== 租约锁 (谁在执行 + 多久过期) ===========================
    /**
     * 当前锁持有者标识
     */
    private String lockedBy;
    /**
     * 锁定时间 (UTC Instant)
     */
    private Instant lockedAt;
    /**
     * 锁定过期 TTL (秒)
     */
    private Integer lockTtlSec;

    // =========================== 执行前守卫 ===========================

    /**
     * 判断当前是否仍持有有效锁 (租约未过期)
     *
     * @param now 当前时间戳
     * @return true 仍持有有效锁, false 已过期或无锁
     */
    public boolean hasValidLock(Instant now) {
        return lockedBy != null && lockedAt != null && lockTtlSec != null
                && lockedAt.plusSeconds(lockTtlSec).isAfter(now);
    }

    /**
     * 确认是否可以执行 (处于 RUNNING 状态且锁仍有效)
     *
     * @param now 当前时间戳
     */
    public void ensureRunnable(Instant now) {
        if (status != TaskStatus.RUNNING)
            throw new IllegalStateException("任务状态不为 RUNNING, 当前状态: " + status);
        if (!hasValidLock(now))
            throw new IllegalStateException("锁已过期或无锁");
    }

    /**
     * 判断当前页码是否已处理完毕 (nextPage <= page)
     *
     * @param page 需要判断的页码
     * @return 是否已经处理完毕
     */
    public boolean shouldSkipPage(int page) {
        return page < nextPage;
    }

    // =========================== 锁的获取 / 释放 / 续约 ===========================

    /**
     * 尝试抢占租约锁, 当前任务状态必须为 PENDING 或 RUNNING, 且当前锁必须无效或过期
     *
     * @param workerId 加锁者标识 (如 workerId)
     * @param now      当前时间戳
     * @param ttlSec   锁过期时间 (秒)
     */
    public void acquire(String workerId, Instant now, int ttlSec) {
        ensureNotTerminal();
        ensureStatus(TaskStatus.PENDING, TaskStatus.RUNNING);
        ensureTtl(ttlSec);
        if (hasValidLock(now) && !workerId.equals(this.lockedBy))
            throw new IllegalStateException("已经被其他人锁定, 当前锁持有者: " + lockedBy);
        this.lockedBy = workerId;
        this.lockedAt = now;
        this.lockTtlSec = ttlSec;
        // 如果当前任务状态为 PENDING, 则置为 RUNNING
        if (this.status == TaskStatus.PENDING)
            this.status = TaskStatus.RUNNING;
    }

    /**
     * 续约租约锁, 只能由持有者续约
     *
     * @param workerId 续约者标识
     * @param now      当前时间戳
     * @param ttlSec   锁过期时间 (秒)
     */
    public void renew(String workerId, Instant now, int ttlSec) {
        ensureNotTerminal();
        ensureTtl(ttlSec);
        if (!workerId.equals(this.lockedBy))
            throw new IllegalStateException("不是当前锁的持有者, 不可续期, 当前锁持有者: " + workerId);
        if (!hasValidLock(now))
            throw new IllegalStateException("锁已过期, 不可续期");
        this.lockedAt = now;
        this.lockTtlSec = ttlSec;
    }

    /**
     * 释放锁, 将所有锁相关字段置空
     */
    public void releaseLock() {
        this.lockedBy = null;
        this.lockedAt = null;
        this.lockTtlSec = null;
    }

    // =========================== 进度推进与收尾 ===========================

    /**
     * 只允许顺序推进, 刚处理完的页码必须等于 nextPage
     *
     * @param pageJustProcessed 刚处理完的页码
     */
    public void onPageProcessed(int pageJustProcessed) {
        ensureStatus(TaskStatus.RUNNING);
        if (pageJustProcessed != nextPage)
            throw new IllegalArgumentException("刚处理完的页码 '" + pageJustProcessed + "' 必须等于下一页页码 " + nextPage);
        // 可选：若 totalPage 已知，校验范围
        if (totalPage != null && (pageJustProcessed < 1 || pageJustProcessed > totalPage))
            throw new IllegalArgumentException("页码超出范围: " + pageJustProcessed + " / total=" + totalPage);
        this.nextPage = pageJustProcessed + 1;
        this.lastError = null;
    }

    /**
     * 设置总页数, 允许增量更新 (服务端滚动扩容)
     *
     * @param total 总页数
     */
    public void setTotalPage(int total) {
        if (total < 0)
            throw new IllegalArgumentException("totalPage 不能为负数");
        if (this.totalPage != null && total < this.totalPage)
            // 如果服务端滚动扩容，允许调大但不允许调小，避免提前 DONE
            throw new IllegalStateException("totalPage 不允许调小, 当前=" + this.totalPage + ", 新值=" + total);
        this.totalPage = total;
    }

    /**
     * 只有在所有页都处理完之后才能将任务标记为 DONE, 并释放锁
     */
    public void markDone() {
        if (!isFinished()) {
            throw new IllegalStateException("还有未处理的页码, 不能标记为 DONE 即将要处理的页码: '" + nextPage + "', 总页数: '" + totalPage + "'");
        }
        this.status = TaskStatus.DONE;
        releaseLock();
    }

    /**
     * 将任务标记为 EXPIRED, 并释放锁
     */
    public void markExpired() {
        ensureNotTerminal();
        this.status = TaskStatus.EXPIRED;
        releaseLock();
    }

    /**
     * 取消任务 (CANCELLED), 并释放锁
     */
    public void cancel() {
        ensureNotTerminal();
        this.status = TaskStatus.CANCELLED;
        releaseLock();
    }

    // =========================== 错误分层 ===========================

    /**
     * 记录一次错误，但保持 RUNNING 状态, attempts + 1 (交由上层决定是否继续 /重 试)
     *
     * @param err 错误描述
     */
    public void recordError(String err) {
        ensureStatus(TaskStatus.RUNNING);
        this.attempts++;
        this.lastError = err;
    }

    /**
     * 将任务标记为 FAILED (不可再继续), 并释放锁
     *
     * @param err 错误描述
     */
    public void markFailed(String err) {
        ensureNotTerminal();
        this.status = TaskStatus.FAILED;
        this.lastError = err;
        releaseLock();
    }

    // =========================== 内部工具 ===========================

    /**
     * 判断当前任务是否处于终态 (DONE/FAILED/EXPIRED/CANCELLED)
     *
     * @return 是否处于终态
     */
    private boolean isTerminal() {
        return status == TaskStatus.DONE || status == TaskStatus.FAILED
                || status == TaskStatus.EXPIRED || status == TaskStatus.CANCELLED;
    }

    /**
     * 判断当前任务是否处于可执行状态 (RUNNING/PENDING)
     */
    private void ensureNotTerminal() {
        if (isTerminal())
            throw new IllegalStateException("任务已处于终态: " + status);
    }

    /**
     * 判断当前任务状态是否在 allowed 允许范围内
     *
     * @param allowed 允许的状态列表
     */
    private void ensureStatus(TaskStatus... allowed) {
        for (TaskStatus s : allowed)
            if (status == s)
                return;
        throw new IllegalStateException("当前状态不允许该操作, status=" + status);
    }

    /**
     * 判断给定的 ttlSec 是否有效
     *
     * @param ttlSec 给定的 ttlSec
     */
    private void ensureTtl(int ttlSec) {
        if (ttlSec <= 0)
            throw new IllegalArgumentException("ttlSec 必须为正整数");
    }

    /**
     * 判断当前任务是否已完成 (所有页都处理完毕)
     *
     * @return 是否已完成
     */
    public boolean isFinished() {
        // totalPage 允许为 null（未知总页数）；若未知，则以“没有可处理页”由应用层控制完成时机
        return totalPage != null && nextPage != null && nextPage > totalPage;
    }
}