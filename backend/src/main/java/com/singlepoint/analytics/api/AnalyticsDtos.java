package com.singlepoint.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() { }

    public record Point(Instant periodStart,
                        long ticketsCreated, long ticketsResolved,
                        long offersRedeemed, long newUsers, BigDecimal revenue) { }

    public record Totals(long communities, long activeCommunities,
                         long residents, long providers,
                         long openTickets, BigDecimal mrr) { }

    public record AnalyticsView(String bucket, List<Point> series, Totals totals) { }
}
