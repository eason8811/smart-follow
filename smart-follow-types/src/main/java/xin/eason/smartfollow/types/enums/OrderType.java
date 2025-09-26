package xin.eason.smartfollow.types.enums;

/**
 * 订单类型枚举, 用于标识订单的不同执行方式
 * <ul>
 *     <li><code>MARKET</code>: 市价单, 按照当前市场价格立即成交</li>
 *     <li><code>LIMIT</code>: 限价单, 只有当市场价格达到或优于指定价格时才会成交</li>
 *     <li><code>POST_ONLY</code>: 只做挂单, 如果下单会立即与已有订单匹配则该订单不会被提交</li>
 *     <li><code>FOK (Fill Or Kill)</code>: 全部成交或取消, 要求订单必须全部成交否则将被取消</li>
 *     <li><code>IOC (Immediate Or Cancel)</code>: 立即成交或取消, 订单中可立即成交的部分会被执行, 不能立即成交的部分将被取消</li>
 * </ul>
 */
public enum OrderType {
    MARKET, LIMIT, POST_ONLY, FOK, IOC;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }
}
