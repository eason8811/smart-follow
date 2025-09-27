package xin.eason.smartfollow.types.enums;

/**
 * 订单状态枚举, 用于标识订单的不同状态
 * <ul>
 *     <li><code>OPEN</code>: 开启</li>
 *     <li><code>PARTIALLY_CLOSED</code>: 部分平仓</li>
 *     <li><code>CLOSED</code>: 平仓</li>
 *     <li><code>CANCELED</code>: 取消</li>
 * </ul>
 */
public enum TradeStatus {
    OPEN, PARTIALLY_CLOSED, CLOSED, CANCELED;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}
