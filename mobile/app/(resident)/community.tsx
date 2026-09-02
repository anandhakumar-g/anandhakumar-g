import { Stack, useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import { Alert, Pressable, View } from "react-native";
import { communities } from "@/api/endpoints";
import { TenantCard } from "@/api/types";
import { Divider, Pill } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function MyCommunities() {
  const { theme } = useTheme();
  const router = useRouter();
  const { me, signIn, switchCommunity, leaveCommunity } = useSession();
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TenantCard[]>([]);
  const [selected, setSelected] = useState<TenantCard | null>(null);
  const [invite, setInvite] = useState("");
  const [flatLabel, setFlatLabel] = useState("");

  const active = (me?.memberships ?? []).filter((m) => m.status === "ACTIVE");

  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        setResults((await communities.search(query.trim() || undefined)).content);
      } catch {
        /* ignore */
      }
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  async function run(key: string, fn: () => Promise<any>) {
    setBusy(key);
    setErr(null);
    try {
      await fn();
      router.replace("/");
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(null);
    }
  }

  function confirmLeave(tenantId: string, name: string) {
    Alert.alert("Leave " + name + "?", "You'll lose access to this community's tickets and offers.", [
      { text: "Cancel", style: "cancel" },
      { text: "Leave", style: "destructive", onPress: () => run("leave" + tenantId, () => leaveCommunity(tenantId)) },
    ]);
  }

  async function join() {
    if (!selected) return;
    await run("join", async () => {
      const s = await communities.join(
        selected.id,
        invite.trim() || undefined,
        invite.trim() ? undefined : flatLabel.trim() || undefined
      );
      await signIn(s);
    });
  }

  return (
    <Screen>
      <Stack.Screen options={{ headerShown: true, title: "My communities" }} />
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}

      {active.map((m) => (
        <Card key={m.tenantId} style={{ gap: theme.space(2) }}>
          <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
            <AppText weight="700" style={{ flexShrink: 1 }}>{m.tenantName ?? m.tenantId}</AppText>
            {m.tenantId === me?.activeTenantId ? <Pill text="active" tone="primary" /> : null}
          </View>
          <AppText size="xs" tone="faint">
            {m.flatLabel ? m.flatLabel + " · " : ""}{m.householdRole.toLowerCase()}
          </AppText>
          <View style={{ flexDirection: "row", gap: theme.space(2), flexWrap: "wrap" }}>
            {m.tenantId !== me?.activeTenantId ? (
              <Button
                label="Switch to this"
                fullWidth={false}
                loading={busy === "switch" + m.tenantId}
                onPress={() => run("switch" + m.tenantId, () => switchCommunity(m.tenantId))}
              />
            ) : null}
            <Button
              label="Leave"
              variant="danger"
              fullWidth={false}
              loading={busy === "leave" + m.tenantId}
              onPress={() => confirmLeave(m.tenantId, m.tenantName ?? "this community")}
            />
          </View>
        </Card>
      ))}

      <Divider />
      <AppText weight="700">Find another community</AppText>
      <Field placeholder="Search by name or area" value={query} onChangeText={setQuery} />
      {results.map((t) => (
        <Pressable key={t.id} onPress={() => setSelected(t)}>
          <Card style={{ borderColor: selected?.id === t.id ? theme.color.primary : theme.color.border,
                         borderWidth: selected?.id === t.id ? 2 : 1 }}>
            <AppText weight="700">{t.name}</AppText>
            <AppText size="sm" tone="muted">{[t.locality, t.city].filter(Boolean).join(", ") || "—"}</AppText>
          </Card>
        </Pressable>
      ))}
      {selected ? (
        <>
          <AppText weight="700">Join {selected.name}</AppText>
          <Field label="Invite code" placeholder="From the community admin" autoCapitalize="characters"
                 value={invite} onChangeText={setInvite} />
          {!invite.trim() ? (
            <Field label="Your flat (for approval)" placeholder="e.g. A-101" value={flatLabel}
                   onChangeText={setFlatLabel} />
          ) : null}
          <Button label={invite.trim() ? "Join now" : "Request to join"} onPress={join} loading={busy === "join"} />
        </>
      ) : null}
    </Screen>
  );
}
