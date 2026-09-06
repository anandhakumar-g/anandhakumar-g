import { useFocusEffect } from "expo-router";
import React, { useCallback } from "react";
import { catalog, tickets } from "@/api/endpoints";
import { EmptyState } from "@/components/Bits";
import { Screen } from "@/components/Themed";
import { TicketRow } from "@/components/TicketRow";
import { useAsync } from "@/hooks/useAsync";

export default function ResidentTickets() {
  const cats = useAsync(() => catalog.categories(), []);
  const list = useAsync(() => tickets.list(undefined, 0), []);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  const catName = (id: string) => cats.data?.find((c) => c.id === id)?.name;

  if (list.loading) return <Screen loading />;
  const items = list.data?.content ?? [];

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      {items.length === 0 ? (
        <EmptyState title="No tickets" body="Tickets you raise will show up here with live status." />
      ) : (
        items.map((t) => (
          <TicketRow key={t.id} ticket={t} hrefBase="/(resident)/ticket" categoryName={catName(t.categoryId)} />
        ))
      )}
    </Screen>
  );
}
