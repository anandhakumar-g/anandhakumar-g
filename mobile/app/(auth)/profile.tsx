import { useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { auth } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function Profile() {
  const { theme } = useTheme();
  const router = useRouter();
  const { signIn, signOut } = useSession();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setError(null);
    setBusy(true);
    try {
      const session = await auth.completeProfile(name.trim(), email.trim() || undefined);
      await signIn(session);
      router.replace("/");
    } catch (e: any) {
      setError(e.message ?? "Could not save profile");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <View style={{ gap: theme.space(4), marginTop: theme.space(6) }}>
        <AppText size="xl" weight="700">
          Tell us about you
        </AppText>
        <Field label="Full name" placeholder="Your name" value={name} onChangeText={setName} autoCapitalize="words" />
        <Field
          label="Email (optional)"
          placeholder="you@example.com"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          error={error}
        />
        <Button label="Continue" onPress={save} loading={busy} disabled={name.trim().length < 2} />
        <Button label="Sign out" variant="ghost" onPress={signOut} />
      </View>
    </Screen>
  );
}
