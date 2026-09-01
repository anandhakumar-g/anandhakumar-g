import { useLocalSearchParams } from "expo-router";
import React from "react";
import { TicketDetail } from "@/components/TicketDetail";

export default function ProviderJobDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <TicketDetail ticketId={String(id)} role="PROVIDER" />;
}
