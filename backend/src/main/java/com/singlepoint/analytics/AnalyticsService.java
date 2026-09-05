package com.singlepoint.analytics;

import com.singlepoint.analytics.api.AnalyticsDtos.AnalyticsView;
import com.singlepoint.analytics.api.AnalyticsDtos.Point;
import com.singlepoint.analytics.api.AnalyticsDtos.Totals;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MVP-11 (B): time-bucketed platform metrics + "now" totals. Super Admin only. */
@Service
public class AnalyticsService {

    private final AnalyticsRepository repo;

    public AnalyticsService(AnalyticsRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public AnalyticsView platform(String bucketRaw, int pointsRaw) {
        String bucket = bucketRaw == null ? "WEEK" : bucketRaw.trim().toUpperCase();
        if (!bucket.equals("WEEK") && !bucket.equals("MONTH")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "bucket must be WEEK or MONTH");
        }
        int points = Math.min(Math.max(pointsRaw, 1), 52);

        List<Instant> starts = bucketStarts(bucket, points);
        Instant since = starts.get(0);

        Map<Instant, long[]> counts = new LinkedHashMap<>();
        for (Instant s : starts) counts.put(s, new long[4]); // [created, resolved, redeemed, users]
        Map<Instant, BigDecimal> revenue = new LinkedHashMap<>();
        for (Instant s : starts) revenue.put(s, BigDecimal.ZERO);

        tally(counts, starts, bucket, repo.ticketCreatedAt(since), 0);
        tally(counts, starts, bucket, repo.ticketResolvedAt(since), 1);
        tally(counts, starts, bucket, repo.offerRedeemedAt(since), 2);
        tally(counts, starts, bucket, repo.userCreatedAt(since), 3);
        for (Object[] row : repo.paidInvoicesSince(since)) {
            Instant paidAt = ((java.sql.Timestamp) row[0]).toInstant();
            BigDecimal amount = (BigDecimal) row[1];
            Instant b = bucketOf(paidAt, bucket, starts);
            if (b != null) revenue.merge(b, amount, BigDecimal::add);
        }

        List<Point> series = new ArrayList<>();
        for (Instant s : starts) {
            long[] c = counts.get(s);
            series.add(new Point(s, c[0], c[1], c[2], c[3], revenue.get(s)));
        }

        Totals totals = new Totals(
                repo.communityCount(), repo.activeCommunityCount(),
                repo.userCountByRole("RESIDENT"), repo.userCountByRole("PROVIDER"),
                repo.openTicketCount(),
                repo.monthlyRecurringRevenue() != null ? repo.monthlyRecurringRevenue() : BigDecimal.ZERO);

        return new AnalyticsView(bucket, series, totals);
    }

    private static void tally(Map<Instant, long[]> counts, List<Instant> starts, String bucket,
                              List<java.sql.Timestamp> timestamps, int slot) {
        for (java.sql.Timestamp ts : timestamps) {
            Instant b = bucketOf(ts.toInstant(), bucket, starts);
            if (b != null) counts.get(b)[slot]++;
        }
    }

    /** The bucket-start each timestamp falls into, or null if it precedes the window. */
    private static Instant bucketOf(Instant ts, String bucket, List<Instant> starts) {
        Instant start = startFor(ts, bucket);
        return starts.contains(start) ? start : null;
    }

    private static List<Instant> bucketStarts(String bucket, int points) {
        Instant nowStart = startFor(Instant.now(), bucket);
        List<Instant> out = new ArrayList<>();
        for (int i = points - 1; i >= 0; i--) {
            out.add(bucket.equals("WEEK")
                    ? nowStart.minus(7L * i, ChronoUnit.DAYS)
                    : LocalDate.ofInstant(nowStart, ZoneOffset.UTC).minusMonths(i)
                        .withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        return out;
    }

    private static Instant startFor(Instant ts, String bucket) {
        LocalDate d = LocalDate.ofInstant(ts, ZoneOffset.UTC);
        LocalDate start = bucket.equals("WEEK") ? d.with(DayOfWeek.MONDAY) : d.withDayOfMonth(1);
        return start.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
