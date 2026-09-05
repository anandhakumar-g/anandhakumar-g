import { Stack, useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { onboarding } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Screen } from "@/components/Themed";
import { useTheme } from "@/theme/ThemeProvider";

export default function RegisterCommunity() {
  const { theme } = useTheme();
  const router = useRouter();
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [locality, setLocality] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit() {
    setErr(null);
    setBusy(true);
    try {
      await onboarding.submitCommunity({
        name: name.trim(),
        city: city.trim() || undefined,
        locality: locality.trim() || undefined,
      });
      router.back();
    } catch (e: any) {
      setErr(e?.message ?? "Couldn't submit");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ headerShown: true, title: "Register a community" }} />
      <AppText size="xl" weight="700">
        Register your community
      </AppText>
      <AppText tone="muted" size="sm">
        We'll review your request and get back to you. Once it's approved you become the
        community's admin.
      </AppText>

      <Card style={{ gap: theme.space(2) }}>
        <Field label="Community name" value={name} onChangeText={setName} placeholder="e.g. Palm Grove Residency" />
        <Field label="City" value={city} onChangeText={setCity} />
        <Field label="Locality / area" value={locality} onChangeText={setLocality} />
        {err ? (
          <AppText tone="danger" size="sm">
            {err}
          </AppText>
        ) : null}
        <Button label="Submit for review" onPress={submit} loading={busy} disabled={name.trim().length < 2} />
      </Card>

      <View style={{ height: theme.space(4) }} />
    </Screen>
  );
}
