package xin.eason.smartfollow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OKX API 配置属性
 */
@Data
@ConfigurationProperties(prefix = "okx")
public class OkxProperties {
    /**
     * '/' 斜杠结尾的 OKX API base-url
     */
    private String baseUrl;
    /**
     * API Key
     */
    private String accessKey;
    /**
     * 用来进行 HMAC-SHA256 加密的 Secret Key  
     */
    private String secretKey;
    /**
     * API key 创建时指定的 口令
     */
    private String passphrase;
    /**
     * OKX 服务器时间偏移（毫秒）。
     * 正数表示本地时间将向未来偏移，负数表示向过去偏移。
     * 当 enableTimeSync=false 时，仅使用该固定偏移；当 enableTimeSync=true 时，会与动态偏移叠加。
     */
    private long clockOffsetMs = 0L;
    /**
     * 是否启用启动时的服务器时间自动同步。
     * 开启后将调用 /api/v5/public/time 获取服务器时间，并计算动态偏移（serverTs - localTs）。
     */
    private boolean enableTimeSync = false;
}