import { useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { auth } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Screen } from "@/components/Themed";
import { useTheme } from "@/theme/ThemeProvider";

export default function Login() {
  const { theme } = useTheme();
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      const r = await auth.requestOtp(phone.trim());
      router.push({ pathname: "/(auth)/otp", params: { phone: phone.trim(), dev: r.devCode ?? "" } });
    } catch (e: any) {
      setError(e.message ?? "Could not send code");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen scroll={false}>
      <View style={{ flex: 1, justifyContent: "center", gap: theme.space(5) }}>
        <View style={{ gap: theme.space(2) }}>
          <AppText size="xxl" weight="700">
            Single Point
          </AppText>
          <AppText tone="muted">
            One place for every request in your community. Sign in with your mobile number.
          </AppText>
        </View>

        <Field
          label="Mobile number"
          placeholder="+91 98765 43210"
          keyboardType="phone-pad"
          autoComplete="tel"
          value={phone}
          onChangeText={setPhone}
          error={error}
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        <Button label="Send code" onPress={submit} loading={busy} disabled={phone.trim().length < 8} />

        <AppText size="xs" tone="faint" style={{ textAlign: "center" }}>
          We'll text you a 6-digit verification code.
        </AppText>
      </View>
    </Screen>
  );
}
