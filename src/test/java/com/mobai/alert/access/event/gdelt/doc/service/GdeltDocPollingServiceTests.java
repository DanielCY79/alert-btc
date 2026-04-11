package com.mobai.alert.access.event.gdelt.doc.service;

import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.event.dto.SocialEventDTO;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.access.event.gdelt.doc.dto.GdeltArticleDTO;
import com.mobai.alert.access.event.gdelt.doc.rest.GdeltDocRestClient;
import com.mobai.alert.notification.AlertNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltDocPollingServiceTests {

    @Test
    void shouldIngestAndNotifyNewArticles() {
        GdeltDocRestClient restClient = mock(GdeltDocRestClient.class);
        MarketEventService marketEventService = mock(MarketEventService.class);
        AlertNotificationService alertNotificationService = mock(AlertNotificationService.class);

        GdeltArticleDTO article = new GdeltArticleDTO();
        article.setTitle("SEC delays Bitcoin ETF decision");
        article.setUrl("https://example.com/btc-etf");
        article.setDomain("example.com");
        article.setLanguage("English");
        article.setSourceCountry("United States");
        article.setEventTime(Instant.parse("2026-04-10T15:30:00Z"));
        when(restClient.searchArticles("bitcoin", "1h", 5)).thenReturn(List.of(article));

        MarketEventDTO event = new MarketEventDTO();
        event.setSource("gdelt_doc");
        event.setEventTime(article.getEventTime());
        event.setEntity("BTC");
        when(marketEventService.ingestSocialEvent(any(SocialEventDTO.class))).thenReturn(event);

        GdeltDocPollingService service = new GdeltDocPollingService(restClient, marketEventService, alertNotificationService);
        ReflectionTestUtils.setField(service, "gdeltEnabled", true);
        ReflectionTestUtils.setField(service, "query", "bitcoin");
        ReflectionTestUtils.setField(service, "timespan", "1h");
        ReflectionTestUtils.setField(service, "maxRecords", 5);
        ReflectionTestUtils.setField(service, "sourceQuality", 0.7d);

        service.pollLatestArticles();

        ArgumentCaptor<SocialEventDTO> captor = ArgumentCaptor.forClass(SocialEventDTO.class);
        verify(marketEventService).ingestSocialEvent(captor.capture());
        verify(alertNotificationService).sendMarketEvent(event, article.getTitle(), article.getUrl());
        assertEquals("gdelt_doc", captor.getValue().getSource());
        assertEquals(article.getEventTime(), captor.getValue().getEventTime());
        assertEquals(0.7d, captor.getValue().getSourceQuality());
    }

    @Test
    void shouldSkipAlreadyProcessedArticles() {
        GdeltDocRestClient restClient = mock(GdeltDocRestClient.class);
        MarketEventService marketEventService = mock(MarketEventService.class);
        AlertNotificationService alertNotificationService = mock(AlertNotificationService.class);

        GdeltArticleDTO article = new GdeltArticleDTO();
        article.setTitle("Binance listing rumor cools");
        article.setUrl("https://example.com/binance-rumor");
        article.setEventTime(Instant.parse("2026-04-10T15:30:00Z"));
        when(restClient.searchArticles("bitcoin", "1h", 5)).thenReturn(List.of(article));
        when(marketEventService.ingestSocialEvent(any(SocialEventDTO.class))).thenReturn(new MarketEventDTO());

        GdeltDocPollingService service = new GdeltDocPollingService(restClient, marketEventService, alertNotificationService);
        ReflectionTestUtils.setField(service, "gdeltEnabled", true);
        ReflectionTestUtils.setField(service, "query", "bitcoin");
        ReflectionTestUtils.setField(service, "timespan", "1h");
        ReflectionTestUtils.setField(service, "maxRecords", 5);

        service.pollLatestArticles();
        service.pollLatestArticles();

        verify(marketEventService, times(1)).ingestSocialEvent(any(SocialEventDTO.class));
    }
}
