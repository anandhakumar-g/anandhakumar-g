import { useRouter } from "expo-router";
import React from "react";
import { Pressable, View } from "react-native";
import { TicketView } from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";
import { StatusBadge } from "./Bits";
import { AppText } from "./Themed";

export function TicketRow({
  ticket,
  hrefBase,
  categoryName,
  showRaiser,
}: {
  ticket: TicketView;
  hrefBase: string; // e.g. "/(resident)/ticket" | "/(admin)/ticket" | "/(provider)/job"
  categoryName?: string;
  showRaiser?: boolean;
}) {
  const { theme } = useTheme();
  const router = useRouter();
  const when = new Date(ticket.updatedAt || ticket.createdAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
  return (
    <Pressable
      onPress={() => router.push(`${hrefBase}/${ticket.id}` as any)}
      style={({ pressed }) => ({
        backgroundColor: theme.color.surface,
        borderWidth: 1,
        borderColor: theme.color.border,
        borderRadius: theme.radius.md,
        padding: theme.space(3.5),
        gap: theme.space(2),
        opacity: pressed ? 0.85 : 1,
      })}
    >
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="sm" weight="700" tone="muted">
          {ticket.referenceCode}
          {categoryName ? `  ·  ${categoryName}` : ""}
        </AppText>
        <StatusBadge status={ticket.status} />
      </View>
      <AppText numberOfLines={2}>{ticket.description}</AppText>
      <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
        <AppText size="xs" tone="faint">
          {showRaiser && ticket.raisedBy?.name ? ticket.raisedBy.name : ticket.flatLabel ?? ticket.serviceAddressText}
        </AppText>
        <AppText size="xs" tone="faint">
          {when}
        </AppText>
      </View>
    </Pressable>
  );
}
