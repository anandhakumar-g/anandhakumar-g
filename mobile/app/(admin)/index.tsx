import { useFocusEffect, useRouter } from "expo-router";
import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { catalog, tickets } from "@/api/endpoints";
import { EmptyState, Segmented } from "@/components/Bits";
import { Button } from "@/components/Button";
import { DashboardSummary } from "@/components/DashboardSummary";
import { AppText, Loading, Screen } from "@/components/Themed";
import { TicketRow } from "@/components/TicketRow";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

type Filter = "ALL" | "NEW" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED";

export default function AdminQueue() {
  const { theme } = useTheme();
  const router = useRouter();
  const [filter, setFilter] = useState<Filter>("ALL");
  const cats = useAsync(() => catalog.categories(), []);
  const list = useAsync(() => tickets.list(filter === "ALL" ? undefined : filter, 0), [filter]);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filter])
  );

  const catName = (id: string) => cats.data?.find((c) => c.id === id)?.name;
  const items = list.data?.content ?? [];

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="xl" weight="700">
          Ticket queue
        </AppText>
        <Button label="📣 Announce" variant="secondary" fullWidth={false} onPress={() => router.push("/(admin)/broadcast")} />
      </View>
      <DashboardSummary />
      <Segmented
        value={filter}
        onChange={setFilter}
        options={[
          { value: "ALL", label: "All" },
          { value: "NEW", label: "New" },
          { value: "ASSIGNED", label: "Assigned" },
          { value: "IN_PROGRESS", label: "Active" },
          { value: "RESOLVED", label: "Resolved" },
        ]}
      />
      {list.loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing here" body="No tickets match this filter." />
      ) : (
        <View style={{ gap: theme.space(3) }}>
          {items.map((t) => (
            <TicketRow key={t.id} ticket={t} hrefBase="/(admin)/ticket" categoryName={catName(t.categoryId)} showRaiser />
          ))}
        </View>
      )}
    </Screen>
  );
}
