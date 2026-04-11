package com.mobai.alert.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * 外部网络访问配置。
 * 统一配置项目访问 Binance 时使用的 HTTP 客户端和 WebSocket 客户端，
 * 包括代理、连接超时、读取超时以及心跳间隔等基础网络参数。
 */
@Configuration
public class RestTemplateConfig {

    @Value("${network.proxy.enabled:true}")
    private boolean proxyEnabled;

    @Value("${network.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${network.proxy.port:7890}")
    private int proxyPort;

    @Value("${network.http.connect-timeout-ms:60000}")
    private int connectTimeoutMs;

    @Value("${network.http.read-timeout-ms:30000}")
    private int readTimeoutMs;

    /**
     * 提供通用的 {@link RestTemplate} 实例，供 REST 客户端访问 Binance HTTP 接口。
     *
     * @param factory HTTP 请求工厂
     * @return 配置完成的 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

    /**
     * 构建 Spring 使用的同步 HTTP 请求工厂。
     * 如果启用了代理，这里会统一挂载到所有 REST 请求上。
     *
     * @return 带超时与代理配置的请求工厂
     */
    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        if (proxyEnabled) {
            factory.setProxy(buildProxy());
        }
        return factory;
    }

    /**
     * 提供行情与普通 WebSocket 场景共用的 OkHttpClient。
     * 该客户端采用长连接读取，因此读取超时设置为无限。
     *
     * @return 通用 OkHttpClient
     */
    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ZERO)
                .pingInterval(Duration.ofMinutes(2));
        if (proxyEnabled) {
            builder.proxy(buildProxy());
        }
        return builder.build();
    }

    /**
     * 提供给 CMS 公告流使用的专用 OkHttpClient。
     * 相比通用客户端，这里使用更短的 ping 间隔，以便更积极地维持公告流会话。
     *
     * @return CMS 专用 OkHttpClient
     */
    @Bean("cmsOkHttpClient")
    public OkHttpClient cmsOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ZERO)
                .pingInterval(Duration.ofSeconds(30));
        if (proxyEnabled) {
            builder.proxy(buildProxy());
        }
        return builder.build();
    }

    /**
     * 构建 HTTP 代理对象。
     *
     * @return 代理配置
     */
    private Proxy buildProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
