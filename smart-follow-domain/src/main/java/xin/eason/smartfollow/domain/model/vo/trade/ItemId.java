package xin.eason.smartfollow.domain.model.vo.trade;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import xin.eason.smartfollow.types.exceptions.IllegalParamException;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotBlank;

/**
 * <p>标的标识符</p>
 * <p>由项目类型 <code>itemType (SPOT, SWAP, FUTURES, MARGIN)</code> 等, </p>
 * <p>和交易对名称 <code>symbol</code> 组成, 如 <code>BTC-USDT</code> 或 <code>BTCUSDT</code></p>
 *
 * @param itemType 表示项目类型如 SPOT, SWAP, FUTURES, MARGIN 等
 *                 <ul>
 *                      <li><code>SPOT</code>: 现货</li>
 *                      <li><code>SWAP</code>: 合约</li>
 *                 </ul>
 *                 <p>此字段用于区分不同的交易产品类型</p>
 * @param symbol   标的标识符
 *                 <p>此字段代表具体的交易对名称, 如 <code>BTC-USDT</code> 或 <code>BTCUSDT</code>, 用于唯一确定一个交易对</p>
 */
public record ItemId(String itemType, String symbol) {
    /**
     * 创建一个新的 <code>ItemId</code> 实例
     *
     * @param itemType 项目类型, 如 SPOT, SWAP, FUTURES, MARGIN 等, 该参数不能为空或空白
     * @param symbol   交易对名称, 如 BTC-USDT 或 BTCUSDT, 用于唯一确定一个交易对, 该参数不能为空或空白
     * @throws IllegalParamException 如果 <code>itemType</code> 或 <code>symbol</code> 为 <code>null</code> 或仅包含空白字符
     */
    public ItemId(String itemType, String symbol) {
        requireNotBlank(itemType, "itemType 不能为空");
        requireNotBlank(symbol, "symbol 不能为空");
        this.itemType = itemType.toUpperCase();
        this.symbol = symbol.toUpperCase();
    }

    /**
     * 返回当前 <code>ItemId</code> 实例的字符串表示形式, 该表示由项目类型和交易对名称组成, 格式为 <code>itemType:symbol</code>
     *
     * @return 字符串格式的 <code>ItemId</code>, 其中包括项目的类型 (如 SPOT, SWAP) 和具体的交易对名称 (如 BTC-USDT), 中间用冒号分隔
     */
    @NotNull
    @Contract(pure = true)
    public String toString() {
        return itemType + ":" + symbol;
    }
}
