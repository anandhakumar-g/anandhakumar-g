import { useFocusEffect } from "expo-router";
import React, { useCallback, useMemo } from "react";
import { View } from "react-native";
import { catalog, tickets } from "@/api/endpoints";
import { AppText, Screen } from "@/components/Themed";
import { EmptyState } from "@/components/Bits";
import { DashboardSummary } from "@/components/DashboardSummary";
import { TicketRow } from "@/components/TicketRow";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function ProviderJobs() {
  const { theme } = useTheme();
  const cats = useAsync(() => catalog.categories(), []);
  const list = useAsync(() => tickets.list(undefined, 0), []);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  const catName = (id: string) => cats.data?.find((c) => c.id === id)?.name;
  const items = list.data?.content ?? [];
  const { active, done } = useMemo(() => {
    const done = items.filter((t) => ["RESOLVED", "CLOSED", "REJECTED"].includes(t.status));
    const active = items.filter((t) => !["RESOLVED", "CLOSED", "REJECTED"].includes(t.status));
    return { active, done };
  }, [items]);

  if (list.loading) return <Screen loading />;

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <AppText size="xl" weight="700">
        My jobs
      </AppText>
      <DashboardSummary />
      {items.length === 0 ? (
        <EmptyState title="No jobs yet" body="Tickets a community admin assigns to you will appear here." />
      ) : (
        <>
          <AppText weight="700" tone="muted">
            Active ({active.length})
          </AppText>
          {active.length === 0 ? (
            <AppText tone="faint" size="sm">
              Nothing active.
            </AppText>
          ) : (
            <View style={{ gap: theme.space(3) }}>
              {active.map((t) => (
                <TicketRow key={t.id} ticket={t} hrefBase="/(provider)/job" categoryName={catName(t.categoryId)} showRaiser />
              ))}
            </View>
          )}
          {done.length > 0 ? (
            <>
              <AppText weight="700" tone="muted" style={{ marginTop: theme.space(3) }}>
                Done ({done.length})
              </AppText>
              <View style={{ gap: theme.space(3) }}>
                {done.map((t) => (
                  <TicketRow key={t.id} ticket={t} hrefBase="/(provider)/job" categoryName={catName(t.categoryId)} showRaiser />
                ))}
              </View>
            </>
          ) : null}
        </>
      )}
    </Screen>
  );
}
