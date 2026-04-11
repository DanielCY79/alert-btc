package com.mobai.alert.access.event.service;

import com.mobai.alert.access.event.binance.cms.dto.BinanceAnnouncementDTO;
import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.event.dto.SocialEventDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarketEventServiceTests {

    @Test
    void shouldNormalizeBinanceAnnouncementIntoEvent() {
        MarketEventService service = new MarketEventService();
        BinanceAnnouncementDTO announcement = new BinanceAnnouncementDTO();
        announcement.setPublishDate(Instant.parse("2026-04-10T10:00:00Z").toEpochMilli());
        announcement.setTitle("Binance Will List SOL Perpetual");
        announcement.setBody("Binance announces listing support for SOL.");

        MarketEventDTO event = service.ingestBinanceAnnouncement(announcement);

        assertNotNull(event);
        assertEquals("binance_cms", event.getSource());
        assertEquals("SOL", event.getEntity());
        assertEquals("listing", event.getEventType());
        assertEquals("bullish", event.getSentiment());
    }

    @Test
    void shouldNormalizeSocialEventIntoEvent() {
        MarketEventService service = new MarketEventService();
        SocialEventDTO socialEvent = new SocialEventDTO();
        socialEvent.setEventTime(Instant.parse("2026-04-10T10:00:00Z"));
        socialEvent.setSource("social_event");
        socialEvent.setRawText("SEC lawsuit rumors hit $BTC ETF sentiment");
        socialEvent.setSourceQuality(0.80);

        MarketEventDTO event = service.ingestSocialEvent(socialEvent);

        assertNotNull(event);
        assertEquals("social_event", event.getSource());
        assertEquals("BTC", event.getEntity());
        assertEquals("regulation", event.getEventType());
        assertEquals("bearish", event.getSentiment());
    }
}

