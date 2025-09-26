package xin.eason.smartfollow.types.enums;

/**
 * 交易所标识
 * <ul>
 *     <li><code>OKX</code>: 欧易</li>
 *     <li><code>BINANCE</code>: 币安</li>
 * </ul>
 */
public enum Exchange {
    OKX, BINANCE;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}