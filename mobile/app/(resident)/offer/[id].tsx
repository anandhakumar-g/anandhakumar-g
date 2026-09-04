import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import React, { useState } from "react";
import { Image, Pressable, View } from "react-native";
import { offers as offersApi } from "@/api/endpoints";
import { discountLabel } from "@/api/types";
import { Field } from "@/components/Field";
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
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState("");
  const [rated, setRated] = useState(false);

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

  async function rate() {
    if (stars < 1) return;
    setBusy(true);
    setErr(null);
    try {
      await offersApi.leaveFeedback(o.id, stars, comment.trim() || undefined);
      setRated(true);
    } catch (e: any) {
      setErr(e.message ?? "Could not send feedback");
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
        {o.redemptionsRemaining != null ? (
          <KeyValue k="Left" v={o.redemptionsRemaining === 0 ? "Fully redeemed" : `${o.redemptionsRemaining} of ${o.redemptionLimitTotal}`} />
        ) : null}
        {o.ratingCount > 0 ? (
          <KeyValue k="Rating" v={`★ ${o.ratingAvg?.toFixed(1)} (${o.ratingCount})`} />
        ) : null}
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

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700">{rated ? "Thanks for the feedback" : "Rate this offer"}</AppText>
        {rated ? (
          <AppText size="xs" tone="faint">You can update your rating any time.</AppText>
        ) : (
          <>
            <View style={{ flexDirection: "row", gap: theme.space(1) }}>
              {[1, 2, 3, 4, 5].map((n) => (
                <Pressable key={n} onPress={() => setStars(n)} hitSlop={6}>
                  <AppText size="xxl" style={{ color: n <= stars ? theme.color.primary : theme.color.border }}>
                    ★
                  </AppText>
                </Pressable>
              ))}
            </View>
            <Field placeholder="Anything to add? (optional)" value={comment} onChangeText={setComment} multiline />
            <Button label="Send feedback" fullWidth={false} loading={busy} disabled={stars < 1} onPress={rate} />
          </>
        )}
      </Card>

      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}
    </Screen>
  );
}
