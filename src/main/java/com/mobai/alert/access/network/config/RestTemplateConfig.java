package com.mobai.alert.access.network.config;

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

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

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

    private Proxy buildProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
