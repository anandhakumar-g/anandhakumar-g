import { useLocalSearchParams } from "expo-router";
import React from "react";
import { TicketDetail } from "@/components/TicketDetail";

export default function ResidentTicketDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <TicketDetail ticketId={String(id)} role="RESIDENT" />;
}
