package xin.eason.smartfollow.domain.model.entity.crawl;

import lombok.*;

import java.time.Instant;
import java.util.Objects;

/**
 * 爬取任务日志实体 (不可变事实)
 * <ul>
 *     <li>
 *         记录一次抓取的输入/输出, 时间, 体积, 校验指纹
 *     </li>
 *     <li>
 *         提供 304/短路判断的便捷方法
 *     </li>
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 兼容 ORM
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CrawlLog {
    /**
     * 交易所
     */
    public enum Exchange {OKX, BINANCE}

    /**
     * 主键 ID
     */
    private Long id;
    /**
     * 绑定 Task: 这个日志属于哪个任务
     */
    private String taskId;
    /**
     * 交易所
     */
    private Exchange exchange;
    /**
     * 规范化抓取目标 (URL)
     */
    private String target;
    /**
     * HTTP 方法 (GET/POST/...)
     */
    private String method;
    /**
     * 规范化参数 JSON
     */
    private String requestParamsJson;
    /**
     * 规范化参数哈希 (与 Task 保持一致的算法)
     */
    private String paramsHash;
    // =========================== 时间 ===========================
    /**
     * 请求开始时间
     */
    private Instant startedAt;
    /**
     * 响应结束时间
     */
    private Instant finishedAt;

    // =========================== HTTP 响应 ===========================
    /**
     * HTTP 响应代码 (允许为 null, 即本地异常未发起请求)
     */
    private Integer statusCode;
    /**
     * 请求是否成功 (由工厂方法统一计算)
     */
    private Boolean success;
    /**
     * 是否 304/短路 (依照 etag / lastModified 推断)
     */
    private Boolean notModified;
    /**
     * 相应字节数
     */
    private Long contentLength;

    // =========================== 内容相同校验 ===========================
    /**
     * HTTP 内容校验指纹
     */
    private String etag;
    /**
     * HTTP 内容的最后修改时间 (原始字段)
     */
    private String lastModifiedRaw;
    /**
     * HTTP 内容的最后修改时间 (解析后 若能解析)
     */
    private Instant lastModifiedAt;
    /**
     * HTTP 内容哈希值 (SHA-256)
     */
    private String contentHash;
    /**
     * 错误信息 (失败时)
     */
    private String errorMsg;

    // =========================== 衍生方法 ===========================

    /**
     * 计算请求耗时 (毫秒), 若时间缺失则返回 -1
     */
    public long durationMs() {
        if (startedAt == null || finishedAt == null)
            return -1L;
        long d = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        return Math.max(0L, d);
    }

    /**
     * 基于 etag/lastModified 判断 "是否未修改"
     *
     * @param newEtag 新 etag
     * @param newLastModifiedAt 新 lastModifiedAt
     * @return 判断结果
     */
    public boolean notModifiedBy(String newEtag, Instant newLastModifiedAt) {
        boolean etagSame = this.etag != null && this.etag.equals(newEtag);
        boolean lmSame = this.lastModifiedAt != null && this.lastModifiedAt.equals(newLastModifiedAt);
        return etagSame || lmSame;
    }

    /**
     * 判断内容是否相同 (同 target & 两者 success & (内容哈希相同 | etag/lastModified 相同))
     * @param prevLog 上一次的日志
     * @return 判断结果
     */
    public boolean sameContentAs(CrawlLog prevLog) {
        return Boolean.TRUE.equals(this.success)
                && Boolean.TRUE.equals(prevLog.success)
                && Objects.equals(this.target, prevLog.target)
                && (
                // 优先依据 etag/lastModified, 否则回退 contentHash
                (this.etag != null && this.etag.equals(prevLog.etag))
                        || (this.lastModifiedAt != null && this.lastModifiedAt.equals(prevLog.lastModifiedAt))
                        || (this.contentHash != null && this.contentHash.equals(prevLog.contentHash))
        );
    }

    // ---------- 工厂方法 (统一规则)  ----------

    /**
     * 成功的日志工厂方法
     *
     * @param exchange 交易所
     * @param taskId 属于的任务 ID
     * @param target 抓取目标
     * @param method HTTP 方法
     * @param requestParamsJson 请求参数 JSON
     * @param paramsHash 参数哈希
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     * @param statusCode HTTP 响应代码
     * @param contentLength 内容字节数
     * @param etag HTTP 内容校验指纹
     * @param lastModifiedRaw HTTP 内容的最后修改时间 (原始字段)
     * @param lastModifiedAt HTTP 内容的最后修改时间 (解析后)
     * @param contentHash HTTP 内容哈希值
     * @return 成功的日志
     */
    public static CrawlLog success(Exchange exchange,
                                   String taskId,
                                   String target,
                                   String method,
                                   String requestParamsJson,
                                   String paramsHash,
                                   Instant startedAt,
                                   Instant finishedAt,
                                   Integer statusCode,
                                   Long contentLength,
                                   String etag,
                                   String lastModifiedRaw,
                                   Instant lastModifiedAt,
                                   String contentHash) {

        // 开始时间/结束时间/状态码不能为空
        requireNonNull(startedAt, "startedAt");
        requireNonNull(finishedAt, "finishedAt");
        requireNonNull(statusCode, "statusCode");
        if (contentLength != null && contentLength < 0)
            throw new IllegalArgumentException("contentLength 不能为负");

        // 判断状态码是否为成功的状态码 (2xx/304)
        boolean is2xx = statusCode / 100 == 2;
        boolean is304 = statusCode == 304;

        return CrawlLog.builder()
                .exchange(exchange)
                .taskId(taskId)
                .target(target)
                .method(method)
                .requestParamsJson(requestParamsJson)
                .paramsHash(paramsHash)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .statusCode(statusCode)
                .success(is2xx || is304)
                .notModified(is304)
                .contentLength(contentLength)
                .etag(etag)
                .lastModifiedRaw(lastModifiedRaw)
                .lastModifiedAt(lastModifiedAt)
                .contentHash(contentHash)
                .build();
    }

    /**
     * 失败的日志工厂方法
     *
     * @param exchange 交易所
     * @param taskId 属于的任务 ID
     * @param target 抓取目标
     * @param method HTTP 方法
     * @param requestParamsJson 请求参数 JSON
     * @param paramsHash 参数哈希
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     * @param statusCode HTTP 响应代码
     * @param errorMsg 错误信息
     * @return 失败的日志
     */
    public static CrawlLog failure(Exchange exchange,
                                   String taskId,
                                   String target,
                                   String method,
                                   String requestParamsJson,
                                   String paramsHash,
                                   Instant startedAt,
                                   Instant finishedAt,
                                   Integer statusCode,
                                   String errorMsg) {

        // 开始时间/结束时间/状态码不能为空
        requireNonNull(startedAt, "startedAt");
        requireNonNull(finishedAt, "finishedAt");
        requireNonNull(statusCode, "statusCode");

        return CrawlLog.builder()
                .exchange(exchange)
                .taskId(taskId)
                .target(target)
                .method(method)
                .requestParamsJson(requestParamsJson)
                .paramsHash(paramsHash)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .statusCode(statusCode)
                .success(false)
                .notModified(false)
                .errorMsg(errorMsg)
                .build();
    }

    /**
     * 检查值是否为空, 为空则抛出 IllegalArgumentException, 错误信息为 "name + 不能为空"
     * @param value 值
     * @param name 名称
     * @throws IllegalArgumentException 非法参数异常
     */
    private static void requireNonNull(Object value, String name) {
        if (value == null)
            throw new IllegalArgumentException(name + " 不能为空");
    }
}
