package xin.eason.smartfollow.domain.model.vo.project;

import lombok.Data;

/**
 * <p>获取交易员排名的请求参数结构</p>
 * 对应 <code>GET /api/v5/copytrading/public-lead-traders</code> 的查询条件
 */
@Data
public class LeadTradersQuery {
    /**
     * 产品类型 (<code>SWAP</code>: 永续合约, <code>SPOT</code>: 现货)
     */
    private String instType = "SWAP";
    /**
     * 排序字段
     * <ul>
     *      <li><code>overview</code>: 综合排序, 默认值</li>
     *      <li><code>pnl</code>: 按照交易员收益额排序</li>
     *      <li><code>aum</code>: 按照带单规模排序</li>
     *      <li><code>win_ratio</code>: 胜率</li>
     *      <li><code>pnl_ratio</code>: 收益率</li>
     *      <li><code>current_copy_trader_pnl</code>: 当前跟单人的收益额</li>
     * </ul>
     */
    private String sortType = "overview";
    /**
     * 查询的交易员状态
     * <ul>
     *     <li><code>0</code>: 所有交易员, 默认值, 包括有空位的和没有空位的</li>
     *     <li><code>1</code>: 项目有空位的的交易员</li>
     * </ul>
     */
    private String state = "0";
    /**
     * 最短带单时长
     * <ul>
     *      <li><code>1</code>: 7 天</li>
     *      <li><code>2</code>: 30 天</li>
     *      <li><code>3</code>: 90 天</li>
     *      <li><code>4</code>: 180天</li>
     * </ul>
     */
    private String minLeadDays;
    /**
     * 交易员资产范围的最小值, 单位为 USDT
     */
    private String minAssets;
    /**
     * 交易员资产范围的最大值, 单位为 USDT
     */
    private String maxAssets;
    /**
     * 带单规模的最小值, 单位为 USDT
     */
    private String minAum;
    /**
     * 带单规模的最大值, 单位为 USDT
     */
    private String maxAum;
    /**
     * <b>固定分页版本; 第一页拿到后续必须回传, 以避免翻页期间版本漂移</b>
     * <p>排名数据的版本, 14 位数字, 如: <code>20231010182400</code>, 主要在分页时使用</p>
     * <p>每10分钟生成一版, 仅保留最新的5个版本</p>
     * <p>默认使用最近的版本; 不存在时不会报错, 会使用最近的版本</p>
     */
    private String dataVer;
    /**
     * 当前页码, 默认为 1
     */
    private Integer page = 1;
    /**
     * 每页条数, 默认为 20
     */
    private Integer limit = 20;
}
