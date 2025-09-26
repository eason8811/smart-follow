package xin.eason.smartfollow.types.enums;

/**
 * 快照来源
 * <ul>
 *     <li><code>OKX_RANK</code>: 榜单</li>
 *     <li><code>OKX_DETAIL</code>: 详情/规则</li>
 *     <li><code>COMPUTED</code>: 系统计算指标</li>
 * </ul>
 */
public enum SnapshotSource {
    OKX_RANK, OKX_DETAIL, COMPUTED;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}
