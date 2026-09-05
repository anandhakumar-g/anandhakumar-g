package com.singlepoint.analytics;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * MVP-11 (B): raw projections for platform analytics. Bucketing (week / month) is done in
 * {@link AnalyticsService} to keep timezone semantics unambiguous — at demo scale these
 * windowed scans are cheap. Native timestamp columns come back as {@link Timestamp}.
 */
public interface AnalyticsRepository extends Repository<com.singlepoint.tenant.domain.Tenant, java.util.UUID> {

    @Query(value = "select created_at from ticket where created_at >= :since", nativeQuery = true)
    List<Timestamp> ticketCreatedAt(@Param("since") Instant since);

    @Query(value = "select resolved_at from ticket where resolved_at is not null and resolved_at >= :since",
           nativeQuery = true)
    List<Timestamp> ticketResolvedAt(@Param("since") Instant since);

    @Query(value = "select created_at from offer_redemption where created_at >= :since", nativeQuery = true)
    List<Timestamp> offerRedeemedAt(@Param("since") Instant since);

    @Query(value = "select created_at from app_user where created_at >= :since", nativeQuery = true)
    List<Timestamp> userCreatedAt(@Param("since") Instant since);

    @Query(value = "select paid_at, amount from subscription_invoice "
            + "where status = 'PAID' and paid_at is not null and paid_at >= :since", nativeQuery = true)
    List<Object[]> paidInvoicesSince(@Param("since") Instant since);

    // ---- "now" totals ----

    @Query(value = "select count(*) from tenant", nativeQuery = true)
    long communityCount();

    @Query(value = "select count(*) from tenant where status = 'ACTIVE'", nativeQuery = true)
    long activeCommunityCount();

    @Query(value = "select count(*) from app_user where role = :role", nativeQuery = true)
    long userCountByRole(@Param("role") String role);

    @Query(value = "select count(*) from ticket where status <> 'CLOSED'", nativeQuery = true)
    long openTicketCount();

    @Query(value = "select coalesce(sum(p.price_amount), 0) from subscription s "
            + "join subscription_plan p on p.id = s.plan_id where s.status = 'ACTIVE'", nativeQuery = true)
    java.math.BigDecimal monthlyRecurringRevenue();
}
