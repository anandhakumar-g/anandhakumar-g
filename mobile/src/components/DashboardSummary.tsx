import React from "react";
import { View } from "react-native";
import { dashboard as dashboardApi } from "@/api/endpoints";
import { AppText, Card } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

const OPEN = ["NEW", "ACKNOWLEDGED", "ON_HOLD", "REOPENED", "REJECTED"];
const IN_PROGRESS = ["PENDING_RESIDENT_APPROVAL", "ASSIGNED", "ACCEPTED", "IN_PROGRESS"];
const DONE = ["RESOLVED", "CLOSED"];

function sum(byStatus: Record<string, number> | undefined, keys: string[]): number {
  if (!byStatus) return 0;
  return keys.reduce((n, k) => n + (byStatus[k] ?? 0), 0);
}

function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

/** MVP-10 (A): tickets bucketed by status + the action items that need this caller. Self-fetching. */
export function DashboardSummary() {
  const { theme } = useTheme();
  const d = useAsync(() => dashboardApi.get(), []);
  const view = d.data;
  if (d.loading || !view) return null;

  const hasBuckets = Object.keys(view.ticketsByStatus ?? {}).length > 0;
  const tiles = [
    { label: "Open", value: sum(view.ticketsByStatus, OPEN) },
    { label: "In progress", value: sum(view.ticketsByStatus, IN_PROGRESS) },
    { label: "Done", value: sum(view.ticketsByStatus, DONE) },
  ];

  const items: string[] = [];
  if (view.pendingApprovalCount) items.push(`${plural(view.pendingApprovalCount, "job")} awaiting your approval`);
  if (view.resolvedAwaitingCloseCount) items.push(`${plural(view.resolvedAwaitingCloseCount, "job")} resolved — close & rate`);
  if (view.pendingPaymentCount) items.push(`${plural(view.pendingPaymentCount, "payment")} due`);
  if (view.awaitingAcceptCount) items.push(`${plural(view.awaitingAcceptCount, "job")} awaiting your accept`);
  if (view.unassignedCount) items.push(`${plural(view.unassignedCount, "ticket")} unassigned`);
  if (view.slaBreachedCount) items.push(`${plural(view.slaBreachedCount, "ticket")} past SLA`);
  if (view.pendingJoinRequestsCount) items.push(`${plural(view.pendingJoinRequestsCount, "join request")} pending`);
  if (view.providersPendingVerificationCount) items.push(`${plural(view.providersPendingVerificationCount, "provider")} awaiting verification`);
  if (view.offersPendingApprovalCount) items.push(`${plural(view.offersPendingApprovalCount, "offer")} awaiting approval`);

  if (!hasBuckets && view.role !== "SUPER_ADMIN" && !view.ratingCount && items.length === 0) return null;

  return (
    <Card style={{ gap: theme.space(2) }}>
      {hasBuckets ? (
        <View style={{ flexDirection: "row" }}>
          {tiles.map((t) => (
            <View key={t.label} style={{ flex: 1, alignItems: "center", gap: theme.space(0.5) }}>
              <AppText size="xl" weight="700">
                {t.value}
              </AppText>
              <AppText size="xs" tone="faint">
                {t.label}
              </AppText>
            </View>
          ))}
        </View>
      ) : null}

      {view.role === "SUPER_ADMIN" ? (
        <AppText size="sm" tone="faint" style={{ textAlign: "center" }}>
          {plural(view.totalOpenTickets ?? 0, "open ticket")} across {plural(view.communityCount ?? 0, "community")}
          {view.communityLessOpenTickets ? ` (${view.communityLessOpenTickets} community-less)` : ""}
        </AppText>
      ) : null}

      {view.ratingCount ? (
        <AppText size="xs" tone="faint" style={{ textAlign: "center" }}>
          ★ {view.ratingAvg?.toFixed(1)} ({view.ratingCount})
        </AppText>
      ) : null}

      {items.length > 0 ? (
        <View style={{ gap: theme.space(1) }}>
          {items.map((t, i) => (
            <AppText key={i} size="sm" tone="primary">
              • {t}
            </AppText>
          ))}
        </View>
      ) : null}
    </Card>
  );
}
