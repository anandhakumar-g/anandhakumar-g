import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import React, { useState } from "react";
import { Image, View } from "react-native";
import { offers as offersApi } from "@/api/endpoints";
import { discountLabel } from "@/api/types";
import { KeyValue } from "@/components/Bits";
import { Button } from "@/components/Button";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function OfferDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { theme } = useTheme();
  const router = useRouter();
  const q = useAsync(() => offersApi.get(id!), [id]);
  const [code, setCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  if (q.loading) return <Loading />;
  if (!q.data) return <Screen><AppText tone="danger">Offer not found.</AppText></Screen>;
  const o = q.data;

  async function redeem() {
    setBusy(true);
    setErr(null);
    try {
      const r = await offersApi.redeem(o.id, o.couponCode ?? undefined);
      setCode(r.couponCode ?? o.couponCode ?? "REDEEMED");
    } catch (e: any) {
      setErr(e.message ?? "Could not redeem");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ headerShown: false }} />
      <AppText tone="primary" size="sm" onPress={() => router.back()}>
        ← Deals
      </AppText>

      {o.imageUrl ? (
        <Image source={{ uri: o.imageUrl }} style={{ width: "100%", height: 180, borderRadius: theme.radius.lg }} resizeMode="cover" />
      ) : null}

      <AppText size="xxl" weight="700">
        {o.title}
      </AppText>
      <View
        style={{
          alignSelf: "flex-start",
          backgroundColor: theme.color.primary,
          borderRadius: theme.radius.pill,
          paddingHorizontal: theme.space(3),
          paddingVertical: theme.space(1.5),
        }}
      >
        <AppText weight="700" style={{ color: theme.color.primaryText }}>
          {discountLabel(o)}
        </AppText>
      </View>

      {o.description ? <AppText tone="muted">{o.description}</AppText> : null}

      <Card style={{ gap: theme.space(1) }}>
        <KeyValue k="Valid from" v={new Date(o.validFrom).toLocaleDateString()} />
        <KeyValue k="Valid to" v={new Date(o.validTo).toLocaleDateString()} />
        <KeyValue k="Per person" v={`${o.redemptionLimitPerUser} redemption${o.redemptionLimitPerUser > 1 ? "s" : ""}`} />
        {o.terms ? <KeyValue k="Terms" v={o.terms} /> : null}
      </Card>

      {code ? (
        <Card style={{ gap: theme.space(1), borderColor: theme.color.success }}>
          <AppText size="sm" tone="success" weight="700">
            Redeemed — show this code
          </AppText>
          <AppText size="xxl" weight="700" family="display" style={{ letterSpacing: 2 }}>
            {code}
          </AppText>
          <AppText size="xs" tone="faint">
            The vendor will confirm usage at the counter.
          </AppText>
        </Card>
      ) : (
        <Button label={o.couponCode ? `Redeem — ${o.couponCode}` : "Redeem"} loading={busy} onPress={redeem} />
      )}
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}
    </Screen>
  );
}
