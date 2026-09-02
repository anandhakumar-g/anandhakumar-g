import React from "react";
import { Pressable, View } from "react-native";
import { providers as providersApi } from "@/api/endpoints";
import { PublicProviderView } from "@/api/types";
import { useAsync } from "@/hooks/useAsync";
import { ratingText } from "@/lib/format";
import { useTheme } from "@/theme/ThemeProvider";
import { Pill } from "./Bits";
import { AppText, Card, Loading } from "./Themed";

/** Resident-facing provider list for Direct-to-Provider booking / re-booking. */
export function DirectProviderPicker({
  onPick,
  onCancel,
  title = "Choose a provider",
}: {
  onPick: (p: PublicProviderView) => void;
  onCancel?: () => void;
  title?: string;
}) {
  const { theme } = useTheme();
  const list = useAsync(() => providersApi.list(), []);

  return (
    <Card style={{ gap: theme.space(2), borderColor: theme.color.primary }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText weight="700">{title}</AppText>
        {onCancel ? (
          <AppText tone="primary" size="sm" onPress={onCancel}>
            Cancel
          </AppText>
        ) : null}
      </View>
      {list.loading ? <Loading /> : null}
      {(list.data ?? []).length === 0 && !list.loading ? (
        <AppText size="sm" tone="faint">No providers available for direct booking yet.</AppText>
      ) : null}
      {(list.data ?? []).map((p) => (
        <Pressable
          key={p.id}
          onPress={() => onPick(p)}
          style={{ padding: theme.space(3), borderRadius: theme.radius.sm, borderWidth: 1, borderColor: theme.color.border }}
        >
          <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
            <AppText weight="600">{p.name}</AppText>
            <View style={{ flexDirection: "row", gap: theme.space(1.5) }}>
              {p.tier === "FEATURED" ? <Pill text="★ Featured" tone="primary" /> : null}
              {ratingText(p.ratingAvg, p.ratingCount) ? <Pill text={ratingText(p.ratingAvg, p.ratingCount)!} /> : null}
              {p.availability !== "AVAILABLE" ? <Pill text={p.availability.toLowerCase()} tone="danger" /> : null}
            </View>
          </View>
          <AppText size="xs" tone="faint">
            {p.vendorCategoryLabel ?? "Service provider"}
            {p.availabilityNote ? ` · ${p.availabilityNote}` : ""}
          </AppText>
        </Pressable>
      ))}
    </Card>
  );
}
