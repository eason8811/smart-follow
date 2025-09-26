package xin.eason.smartfollow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * OKX Retrofit/OkHttp 客户端配置
 * <p>
 * 主要职责：
 * - 从 {@link OkxProperties} 读取配置（baseUrl、API 密钥等），并校验 baseUrl 以 "/" 结尾；
 * - 构建带有 OKX 鉴权拦截器的 OkHttpClient；
 * - 暴露 Retrofit Bean（绑定 Jackson 转换器）；
 * - 暴露一个通用的 API 工厂 Bean（Function<Class<?>, Object>），用于通过 retrofit.create(...) 创建任意 API 接口代理。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OkxProperties.class)
@RequiredArgsConstructor
public class OkxRetrofitConfig {

    /**
     * OKX 相关配置（包含 baseUrl、accessKey、secretKey、passphrase、时钟偏移等）。
     */
    private final OkxProperties okxProperties;
    /**
     * Spring 管理的 Jackson ObjectMapper，用于解析服务器时间响应。
     */
    private final ObjectMapper objectMapper;

    /**
     * 生成 ISO-8601 UTC 时间戳的格式器（示例：2020-12-08T09:08:57.715Z）。
     */
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    /**
     * 运行时动态的服务端时间相对本地时间偏移（毫秒）。
     * 值为 serverTs - localTs。默认 0，初始化或定期同步成功后更新。
     */
    private final AtomicLong serverOffsetMs = new AtomicLong(0L);

    /**
     * 启动时（ApplicationRunner）根据配置决定是否自动对齐服务端时间。
     * 若开启 enableTimeSync，则会调用 OKX 服务器时间接口一次来计算动态偏移。
     * 失败将记录 warn 日志，但不会阻塞启动。
     */
    @Bean
    public ApplicationRunner okxTimeSyncRunner() {
        return args -> initTimeSyncOnce();
    }

    /**
     * 构建 OkHttpClient，并注册 OKX 鉴权拦截器。
     *
     * @return 配置完成的 OkHttpClient
     */
    @Bean
    public OkHttpClient okxOkHttpClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(okxAuthInterceptor())
                .build();
    }

    /**
     * OKX 鉴权拦截器。
     * <p>
     * - 每次请求都会生成 UTC ISO-8601 时间戳（OK-ACCESS-TIMESTAMP）；
     * - 时间戳会叠加固定偏移（clockOffsetMs）与动态偏移（serverOffsetMs）；
     * - 拼接待签名串：timestamp + method + requestPath + body；
     * - 使用 HMAC-SHA256(secretKey) 计算摘要，并进行 Base64 输出（OK-ACCESS-SIGN）；
     * - 补齐 OKX 规定的请求头：OK-ACCESS-KEY、OK-ACCESS-SIGN、OK-ACCESS-TIMESTAMP、OK-ACCESS-PASSPHRASE、Content-Type。
     *
     * @return 用于添加鉴权头的 Interceptor
     */
    @Bean
    public Interceptor okxAuthInterceptor() {
        return chain -> {
            Request original = chain.request();

            // 1) 生成（可修正的）UTC ISO-8601 格式时间戳
            String timestamp = ISO_INSTANT.format(nowWithOffset());

            // 2) 还原 method、path 与查询串
            String method = original.method();
            String requestPath = original.url().encodedPath();
            String query = original.url().encodedQuery();
            if (query != null && !query.isEmpty()) {
                requestPath = requestPath + "?" + query;
            }

            // 3) 仅对可能存在 body 的方法读取 body 参与签名
            String bodyStr = "";
            if (original.body() != null && mayHaveBody(method)) {
                okio.Buffer buffer = new okio.Buffer();
                original.body().writeTo(buffer);
                bodyStr = buffer.readUtf8();
            }

            // 4) 计算签名
            String prehash = timestamp + method + requestPath + bodyStr;
            String sign = hmacSha256Base64(prehash, safe(okxProperties.getSecretKey()));

            // 5) 附加请求头
            Request signed = original.newBuilder()
                    .addHeader("OK-ACCESS-KEY", safe(okxProperties.getAccessKey()))
                    .addHeader("OK-ACCESS-SIGN", sign)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", safe(okxProperties.getPassphrase()))
                    .addHeader("Content-Type", "application/json")
                    .build();
            return chain.proceed(signed);
        };
    }

    /**
     * 构建并暴露 Retrofit 客户端。
     * <p>
     * 注意：OKX 的 baseUrl 必须以 "/" 结尾，否则 Retrofit 会抛出异常，故这里进行校验。
     *
     * @param objectMapper Spring Boot 提供的 Jackson ObjectMapper
     * @return 配置完成的 Retrofit
     */
    @Bean
    public Retrofit okxRetrofit(ObjectMapper objectMapper) {
        String baseUrl = safe(okxProperties.getBaseUrl());
        if (!baseUrl.endsWith("/")) {
            throw new IllegalStateException("okx.base-url 必须以 '/' 结尾");
        }
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okxOkHttpClient())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();
    }

    /**
     * 暴露一个通用的 Retrofit API 工厂 Bean。
     * <p>
     * 使用方式（在其他模块中）：
     * - 注入：Function<Class<YourApi>, YourApi> okxApiFactory
     * - 获取代理：YourApi api = okxApiFactory.apply(YourApi.class)
     * 这样可避免在 app 模块中直接依赖具体接口类型，保持分层依赖正确。
     *
     * @param retrofit 已配置的 Retrofit 客户端
     * @return 通用 API 工厂
     */
    @Bean
    public Function<Class<?>, Object> okxApiFactory(Retrofit retrofit) {
        return retrofit::create;
    }

    // ===== 时间与签名相关的私有辅助方法 =====

    /**
     * 计算“当前时间 + 偏移量”的 Instant。
     * 偏移量 = 配置的固定偏移（clockOffsetMs）+ 动态偏移（serverOffsetMs）。
     */
    private Instant nowWithOffset() {
        long offset = totalOffsetMs();
        return Instant.ofEpochMilli(System.currentTimeMillis() + offset);
    }

    /**
     * 返回当前总偏移量（毫秒）。
     */
    private long totalOffsetMs() {
        return okxProperties.getClockOffsetMs() + serverOffsetMs.get();
    }

    /**
     * 应用启动时进行一次服务器时间对齐（若开启 enableTimeSync）。
     * 成功后会将 serverOffsetMs 设置为 serverTs - localTs。
     */
    private void initTimeSyncOnce() {
        if (!okxProperties.isEnableTimeSync()) {
            log.info("[OKX] 时间对齐未开启（okx.enable-time-sync=false），仅使用固定偏移 clockOffsetMs={}ms", okxProperties.getClockOffsetMs());
            return;
        }
        try {
            long serverTs = fetchOkxServerTs();
            long localTs = System.currentTimeMillis();
            long dynamic = serverTs - localTs;
            serverOffsetMs.set(dynamic);
            log.info("[OKX] 服务端时间对齐成功：serverTs={}, localTs={}, dynamicOffset={}ms, totalOffset={}ms", serverTs, localTs, dynamic, totalOffsetMs());
        } catch (Exception ex) {
            log.warn("[OKX] 服务端时间对齐失败，将继续使用固定偏移 clockOffsetMs={}ms。原因: {}", okxProperties.getClockOffsetMs(), ex.getMessage());
        }
    }

    /**
     * 向 OKX 公开接口获取服务器时间戳（毫秒）。
     * 端点：{baseUrl}/api/v5/public/time
     *
     * @return 服务器时间戳（毫秒）
     */
    private long fetchOkxServerTs() throws IOException {
        String baseUrl = safe(okxProperties.getBaseUrl());
        if (!baseUrl.endsWith("/"))
            baseUrl = baseUrl + "/";

        String url = baseUrl + "api/v5/public/time";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IOException("非预期的响应状态码: " + (resp.code()));

            String json = resp.body().string();
            // 预期返回：{"code":"0","data":[{"ts":"1700000000000"}], ...}
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty())
                throw new IOException("响应结构无效：缺少 data 数组");

            String tsStr = data.get(0).path("ts").asText();
            return Long.parseLong(tsStr);
        }
    }

    /**
     * 使用 HMAC-SHA256(secret) 对内容进行摘要并以 Base64 形式输出。
     *
     * @param content 待签名内容（timestamp + method + requestPath + body）
     * @param secret  OKX 提供的 Secret Key
     * @return Base64 编码的签名字符串
     */
    private static String hmacSha256Base64(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("计算 HMAC-SHA256 签名失败", e);
        }
    }

    /**
     * 判断 HTTP 方法是否可能包含请求体。
     *
     * @param method HTTP 方法名
     * @return true 表示通常会携带 body（POST/PUT/PATCH）
     */
    private static boolean mayHaveBody(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    /**
     * 将 null 安全地转换为空串，避免 NPE 并与签名逻辑一致。
     *
     * @param s 输入字符串
     * @return 非空字符串
     */
    private static String safe(String s) {
        return s == null ? "" : s;
    }
}