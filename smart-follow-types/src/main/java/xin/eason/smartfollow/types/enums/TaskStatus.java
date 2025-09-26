package xin.eason.smartfollow.types.enums;

/**
 * 任务状态
 * <ul>
 *     <li><code>PENDING</code>: 等待执行</li>
 *     <li><code>RUNNING</code>: 正在执行</li>
 *     <li><code>DONE</code>: 已完成</li>
 *     <li><code>FAILED</code>: 失败</li>
 *     <li><code>EXPIRED</code>: 过期</li>
 *     <li><code>CANCELLED</code>: 已取消</li>
 * </ul>
 */
public enum TaskStatus {
    PENDING, RUNNING, DONE, FAILED, EXPIRED, CANCELLED;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}