package xin.eason.smartfollow.types.enums;

/**
 * 交易方向枚举, 用于标识交易的方向或类型
 * <ul>
 *     <li><code>BUY</code>: 买入 (现货)</li>
 *     <li><code>SELL</code>: 卖出 (现货)</li>
 *     <li><code>LONG</code>: 做多 (合约)</li>
 *     <li><code>SHORT</code>: 做空 (合约)</li>
 * </ul>
 */
public enum TradeSide {
    BUY, SELL, LONG, SHORT;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}
