package com.mobai.alert.access.event.gdelt.doc.rest;

import com.mobai.alert.access.event.gdelt.doc.dto.GdeltArticleDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GdeltDocRestClientTests {

    @Test
    void shouldParseArticlesFromJsonResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GdeltDocRestClient client = new GdeltDocRestClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.gdeltproject.org/api/v2/doc/doc");
        ReflectionTestUtils.setField(client, "defaultSort", "datedesc");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("""
                        {
                          "articles": [
                            {
                              "url": "https://example.com/btc-etf",
                              "title": "SEC delays Bitcoin ETF decision",
                              "seendate": "20260410153000",
                              "domain": "example.com",
                              "language": "English",
                              "sourcecountry": "United States",
                              "socialimage": "https://example.com/image.jpg"
                            }
                          ]
                        }
                        """);

        List<GdeltArticleDTO> articles = client.searchArticles("bitcoin", "1h", 5);

        assertEquals(1, articles.size());
        assertEquals("https://example.com/btc-etf", articles.get(0).getUrl());
        assertEquals("SEC delays Bitcoin ETF decision", articles.get(0).getTitle());
        assertEquals("example.com", articles.get(0).getDomain());
        assertEquals(Instant.parse("2026-04-10T15:30:00Z"), articles.get(0).getEventTime());
    }

    @Test
    void shouldReturnEmptyWhenFetchFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GdeltDocRestClient client = new GdeltDocRestClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.gdeltproject.org/api/v2/doc/doc");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new IllegalStateException("boom"));

        List<GdeltArticleDTO> articles = client.searchArticles("bitcoin", "1h", 5);

        assertTrue(articles.isEmpty());
    }
}
