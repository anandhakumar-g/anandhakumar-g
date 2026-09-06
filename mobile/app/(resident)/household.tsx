import { Stack, useLocalSearchParams } from "expo-router";
import React, { useState } from "react";
import { Alert, View } from "react-native";
import { me as meApi } from "@/api/endpoints";
import { Divider, Pill } from "@/components/Bits";
import { Button } from "@/components/Button";
import { AppText, Card, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function Household() {
  const { theme } = useTheme();
  const { flatId } = useLocalSearchParams<{ flatId: string }>();
  const { me } = useSession();
  const roster = useAsync(() => meApi.householdMembers(String(flatId)), [flatId]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [code, setCode] = useState<string | null>(null);

  const myUserId = me?.userId;
  const iAmPrimary = (roster.data ?? []).some(
    (m) => m.userId === myUserId && m.householdRole === "PRIMARY"
  );

  async function invite() {
    setBusy(true);
    setErr(null);
    try {
      const r = await meApi.createHouseholdInvite(String(flatId));
      setCode(r.code);
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  function confirmRemove(userId: string, name: string) {
    Alert.alert("Remove " + name + "?", "They'll lose access to this flat.", [
      { text: "Cancel", style: "cancel" },
      {
        text: "Remove",
        style: "destructive",
        onPress: async () => {
          setBusy(true);
          try {
            await meApi.removeHouseholdMember(String(flatId), userId);
            roster.reload();
          } catch (e: any) {
            setErr(e.message ?? "Failed");
          } finally {
            setBusy(false);
          }
        },
      },
    ]);
  }

  if (roster.loading) return <Screen loading />;

  return (
    <Screen onRefresh={roster.refresh} refreshing={roster.refreshing}>
      <Stack.Screen options={{ headerShown: true, title: "Household" }} />
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}

      {(roster.data ?? []).map((m) => (
        <Card key={m.userId} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <View style={{ flexShrink: 1 }}>
            <AppText weight="700">{m.name ?? m.phoneMasked ?? "Member"}</AppText>
            <AppText size="xs" tone="faint">{m.phoneMasked}</AppText>
          </View>
          <View style={{ flexDirection: "row", gap: theme.space(2), alignItems: "center" }}>
            <Pill text={m.householdRole.toLowerCase()} tone={m.householdRole === "PRIMARY" ? "primary" : "muted"} />
            {iAmPrimary && m.householdRole === "SECONDARY" ? (
              <AppText
                size="sm"
                tone="danger"
                onPress={() => confirmRemove(m.userId, m.name ?? "this member")}
              >
                Remove
              </AppText>
            ) : null}
          </View>
        </Card>
      ))}

      {iAmPrimary ? (
        <>
          <Divider />
          <Card style={{ gap: theme.space(2) }}>
            <AppText weight="700">Add a family member</AppText>
            <AppText size="xs" tone="faint">
              Share this code — they sign in with their own number and join this flat.
            </AppText>
            {code ? (
              <AppText size="xl" weight="700" style={{ letterSpacing: 3, textAlign: "center" }}>
                {code}
              </AppText>
            ) : null}
            <Button label={code ? "New code" : "Generate a code"} onPress={invite} loading={busy} />
          </Card>
        </>
      ) : (
        <AppText size="xs" tone="faint">Only the primary member can add or remove people.</AppText>
      )}
    </Screen>
  );
}
