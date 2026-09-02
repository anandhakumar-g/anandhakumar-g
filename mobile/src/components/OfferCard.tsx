import { useRouter } from "expo-router";
import React from "react";
import { Image, Pressable, View } from "react-native";
import { discountLabel, OfferView } from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";
import { Pill } from "./Bits";
import { AppText } from "./Themed";

export function OfferCard({ offer, hrefBase }: { offer: OfferView; hrefBase: string }) {
  const { theme } = useTheme();
  const router = useRouter();
  const validTo = new Date(offer.validTo).toLocaleDateString(undefined, { month: "short", day: "numeric" });

  return (
    <Pressable
      onPress={() => router.push(`${hrefBase}/${offer.id}` as any)}
      style={({ pressed }) => ({
        backgroundColor: theme.color.surface,
        borderRadius: theme.radius.lg,
        borderWidth: 1,
        borderColor: theme.color.border,
        overflow: "hidden",
        opacity: pressed ? 0.9 : 1,
      })}
    >
      {offer.imageUrl ? (
        <Image source={{ uri: offer.imageUrl }} style={{ width: "100%", height: 140 }} resizeMode="cover" />
      ) : (
        <View style={{ height: 96, backgroundColor: theme.color.surfaceAlt, alignItems: "center", justifyContent: "center" }}>
          <AppText size="xl" family="display" tone="faint">
            {discountLabel(offer)}
          </AppText>
        </View>
      )}
      <View style={{ padding: theme.space(3.5), gap: theme.space(1.5) }}>
        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <View
            style={{
              backgroundColor: theme.color.primary,
              borderRadius: theme.radius.pill,
              paddingHorizontal: theme.space(2.5),
              paddingVertical: theme.space(1),
            }}
          >
            <AppText size="xs" weight="700" style={{ color: theme.color.primaryText }}>
              {discountLabel(offer)}
            </AppText>
          </View>
          {offer.status !== "ACTIVE" ? <Pill text={offer.status.replace(/_/g, " ")} /> : (
            <AppText size="xs" tone="faint">
              till {validTo}
            </AppText>
          )}
        </View>
        <AppText size="lg" weight="700" numberOfLines={1}>
          {offer.title}
        </AppText>
        {offer.description ? (
          <AppText size="sm" tone="muted" numberOfLines={2}>
            {offer.description}
          </AppText>
        ) : null}
        {offer.couponCode ? (
          <AppText size="xs" tone="primary" weight="700" style={{ letterSpacing: 1 }}>
            CODE {offer.couponCode}
          </AppText>
        ) : null}
      </View>
    </Pressable>
  );
}
