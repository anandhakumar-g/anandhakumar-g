import React, { useState } from "react";
import { View } from "react-native";
import { superadmin } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Divider, EmptyState, KeyValue } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function SuperHome() {
  const { theme } = useTheme();
  const { signOut } = useSession();
  const health = useAsync(() => superadmin.tenantHealth(), []);
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [adminPhone, setAdminPhone] = useState("");
  const [adminName, setAdminName] = useState("");
  const [lastTenantId, setLastTenantId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function createTenant() {
    setErr(null);
    setMsg(null);
    try {
      const t = await superadmin.createTenant({ name: name.trim(), city: city.trim() || undefined });
      setLastTenantId(t.id);
      setName("");
      setCity("");
      setMsg(`Created "${t.name}". Now add an admin below.`);
      health.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  async function createAdmin() {
    if (!lastTenantId) return;
    setErr(null);
    setMsg(null);
    try {
      const a = await superadmin.createAdmin(lastTenantId, adminPhone.trim(), adminName.trim());
      setAdminPhone("");
      setAdminName("");
      setMsg(`Admin invited (${a.phoneMasked}). They can now sign in.`);
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  return (
    <Screen onRefresh={health.refresh} refreshing={health.refreshing}>
      <AppText size="xl" weight="700">
        Platform
      </AppText>

      {health.loading ? (
        <Loading />
      ) : (health.data?.length ?? 0) === 0 ? (
        <EmptyState title="No communities yet" body="Onboard your first community below." />
      ) : (
        health.data!.map((t) => (
          <Card key={t.id} style={{ gap: theme.space(1) }}>
            <AppText weight="700">{t.name}</AppText>
            <AppText size="xs" tone="faint">
              {t.city ?? "—"}
            </AppText>
            <KeyValue k="Open tickets" v={String(t.openTickets)} />
            <KeyValue k="Total tickets" v={String(t.totalTickets)} />
          </Card>
        ))
      )}

      <Divider />
      {msg ? (
        <AppText tone="success" size="sm">
          {msg}
        </AppText>
      ) : null}
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      <Card style={{ gap: theme.space(2) }}>
        <AppText weight="700">Onboard a community</AppText>
        <Field label="Name" value={name} onChangeText={setName} />
        <Field label="City" value={city} onChangeText={setCity} />
        <Button label="Create community" onPress={createTenant} disabled={name.trim().length < 2} />
      </Card>

      {lastTenantId ? (
        <Card style={{ gap: theme.space(2) }}>
          <AppText weight="700">Add an admin</AppText>
          <Field label="Admin name" value={adminName} onChangeText={setAdminName} />
          <Field label="Admin phone" value={adminPhone} onChangeText={setAdminPhone} keyboardType="phone-pad" />
          <Button label="Invite admin" onPress={createAdmin} disabled={adminPhone.trim().length < 8 || adminName.trim().length < 2} />
        </Card>
      ) : null}

      <View style={{ marginTop: theme.space(4) }}>
        <Button label="Sign out" variant="danger" onPress={signOut} />
      </View>
    </Screen>
  );
}
