package xin.eason.smartfollow.types.enums;

/**
 * 任务状态
 * <ul>
 *     <li>
 *         PENDING: 等待执行
 *     </li>
 *     <li>
 *         RUNNING: 正在执行
 *     </li>
 *     <li>
 *         DONE: 已完成
 *     </li>
 *     <li>
 *         FAILED: 失败
 *     </li>
 *     <li>
 *         EXPIRED: 过期
 *     </li>
 *     <li>
 *         CANCELLED: 已取消
 *     </li>
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