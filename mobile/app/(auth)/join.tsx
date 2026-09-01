import { useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import { Pressable, View } from "react-native";
import { communities } from "@/api/endpoints";
import { TenantCard } from "@/api/types";
import { Button } from "@/components/Button";
import { Divider } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function Join() {
  const { theme } = useTheme();
  const router = useRouter();
  const { signIn, signOut } = useSession();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TenantCard[]>([]);
  const [selected, setSelected] = useState<TenantCard | null>(null);
  const [invite, setInvite] = useState("");
  const [flatLabel, setFlatLabel] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        const r = await communities.search(query.trim() || undefined);
        setResults(r.content);
      } catch {
        /* ignore */
      }
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  async function join() {
    if (!selected) return;
    setError(null);
    setBusy(true);
    try {
      const session = await communities.join(
        selected.id,
        invite.trim() || undefined,
        invite.trim() ? undefined : flatLabel.trim() || undefined
      );
      await signIn(session);
      router.replace("/");
    } catch (e: any) {
      setError(e.message ?? "Could not join");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen>
      <View style={{ gap: theme.space(3), marginTop: theme.space(4) }}>
        <AppText size="xl" weight="700">
          Find your community
        </AppText>
        <Field placeholder="Search by name or area" value={query} onChangeText={setQuery} />

        {results.map((t) => {
          const active = selected?.id === t.id;
          return (
            <Pressable key={t.id} onPress={() => setSelected(t)}>
              <Card
                style={{
                  borderColor: active ? theme.color.primary : theme.color.border,
                  borderWidth: active ? 2 : 1,
                }}
              >
                <AppText weight="700">{t.name}</AppText>
                <AppText size="sm" tone="muted">
                  {[t.locality, t.city].filter(Boolean).join(", ") || "—"}
                </AppText>
              </Card>
            </Pressable>
          );
        })}
        {results.length === 0 ? (
          <AppText tone="faint" size="sm">
            No communities match that search.
          </AppText>
        ) : null}

        {selected ? (
          <>
            <Divider />
            <AppText weight="700">Join {selected.name}</AppText>
            <Field
              label="Invite code"
              placeholder="From your community admin"
              autoCapitalize="characters"
              value={invite}
              onChangeText={setInvite}
              hint="Have a code? You'll be added instantly."
            />
            {!invite.trim() ? (
              <Field
                label="Your flat (for approval)"
                placeholder="e.g. A-101"
                value={flatLabel}
                onChangeText={setFlatLabel}
                hint="No code? We'll send a join request to the admin."
              />
            ) : null}
            {error ? (
              <AppText tone="danger" size="sm">
                {error}
              </AppText>
            ) : null}
            <Button label={invite.trim() ? "Join now" : "Request to join"} onPress={join} loading={busy} />
          </>
        ) : null}

        <Button label="Sign out" variant="ghost" onPress={signOut} />
      </View>
    </Screen>
  );
}
