import { useRouter } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";
import { me as meApi, superadmin } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Divider, EmptyState, KeyValue, Pill } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function SuperHome() {
  const { theme } = useTheme();
  const router = useRouter();
  const { signOut, signIn } = useSession();
  const health = useAsync(() => superadmin.tenantHealth(), []);
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [adminPhone, setAdminPhone] = useState("");
  const [adminName, setAdminName] = useState("");
  const [lastTenantId, setLastTenantId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [manageId, setManageId] = useState<string | null>(null);

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
      setMsg(`Admin added (${a.phoneMasked}). They can now sign in.`);
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  async function actAsAdmin(tenantId: string) {
    setErr(null);
    try {
      await signIn(await meApi.switchCommunity(tenantId));
      router.replace("/(admin)");
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  return (
    <Screen onRefresh={health.refresh} refreshing={health.refreshing}>
      <AppText size="xl" weight="700">
        Platform
      </AppText>

      <View style={{ flexDirection: "row", gap: theme.space(2) }}>
        <Button label="Audit log" variant="secondary" fullWidth={false} onPress={() => router.push("/(super)/audit")} />
        <Button label="Announcement" variant="secondary" fullWidth={false} onPress={() => router.push("/(super)/broadcast")} />
      </View>

      {health.loading ? (
        <Loading />
      ) : (health.data?.length ?? 0) === 0 ? (
        <EmptyState title="No communities yet" body="Onboard your first community below." />
      ) : (
        health.data!.map((t) => (
          <Card key={t.id} style={{ gap: theme.space(1) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700">{t.name}</AppText>
              <AppText
                size="sm"
                tone="primary"
                onPress={() => setManageId((cur) => (cur === t.id ? null : t.id))}
              >
                {manageId === t.id ? "Close" : "Manage"}
              </AppText>
            </View>
            <AppText size="xs" tone="faint">
              {t.city ?? "—"}
            </AppText>
            <KeyValue k="Open tickets" v={String(t.openTickets)} />
            <KeyValue k="Total tickets" v={String(t.totalTickets)} />
            {manageId === t.id ? <ManageCommunity tenantId={t.id} onActAsAdmin={() => actAsAdmin(t.id)} /> : null}
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
          <Button label="Add admin" onPress={createAdmin} disabled={adminPhone.trim().length < 8 || adminName.trim().length < 2} />
        </Card>
      ) : null}

      <View style={{ marginTop: theme.space(4) }}>
        <Button label="Sign out" variant="danger" onPress={signOut} />
      </View>
    </Screen>
  );
}

function ManageCommunity({ tenantId, onActAsAdmin }: { tenantId: string; onActAsAdmin: () => void }) {
  const { theme } = useTheme();
  const admins = useAsync(() => superadmin.tenantAdmins(tenantId), [tenantId]);
  const [phone, setPhone] = useState("");
  const [aname, setAname] = useState("");
  const [code, setCode] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function run(key: string, fn: () => Promise<any>) {
    setBusy(key);
    setErr(null);
    try {
      await fn();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(null);
    }
  }

  return (
    <View style={{ gap: theme.space(2), marginTop: theme.space(2), backgroundColor: theme.color.surfaceAlt,
                   borderRadius: theme.radius.sm, padding: theme.space(3) }}>
      {err ? <AppText tone="danger" size="xs">{err}</AppText> : null}

      <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>ADMINS</AppText>
      {admins.loading ? (
        <Loading />
      ) : (admins.data?.length ?? 0) === 0 ? (
        <AppText size="xs" tone="faint">No admins — you can act as admin below.</AppText>
      ) : (
        admins.data!.map((a) => (
          <View key={a.userId} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
            <AppText size="sm">{a.name ?? a.phoneMasked ?? a.userId.slice(0, 8)}</AppText>
            <AppText size="xs" tone="danger"
              onPress={() => run("d" + a.userId, async () => { await superadmin.detachAdmin(tenantId, a.userId); admins.reload(); })}>
              Detach
            </AppText>
          </View>
        ))
      )}

      <Field placeholder="New admin name" value={aname} onChangeText={setAname} />
      <Field placeholder="New admin phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
      <Button
        label="Add / attach admin"
        fullWidth={false}
        loading={busy === "add"}
        disabled={phone.trim().length < 8 || aname.trim().length < 2}
        onPress={() => run("add", async () => {
          await superadmin.createAdmin(tenantId, phone.trim(), aname.trim());
          setPhone(""); setAname(""); admins.reload();
        })}
      />

      <Divider />
      <Button
        label="Issue an invite code"
        variant="secondary"
        fullWidth={false}
        loading={busy === "inv"}
        onPress={() => run("inv", async () => {
          const c = await superadmin.createTenantInvite(tenantId, { maxUses: 20, validDays: 30 });
          setCode(c.code);
        })}
      />
      {code ? (
        <Pill text={`Invite: ${code}`} tone="primary" />
      ) : null}

      <Button label="Act as admin for this community" onPress={onActAsAdmin} />
    </View>
  );
}
