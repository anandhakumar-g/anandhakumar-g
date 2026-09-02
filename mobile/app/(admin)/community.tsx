import { useFocusEffect } from "expo-router";
import React, { useCallback, useState } from "react";
import { Pressable, Switch, View } from "react-native";
import { admin, adminCategories } from "@/api/endpoints";
import { FlatView, InviteView, JoinRequestView, TicketCategory } from "@/api/types";
import { Button } from "@/components/Button";
import { Divider, EmptyState, Pill, Segmented } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

type Tab = "requests" | "invites" | "flats" | "settings";

export default function Community() {
  const { theme } = useTheme();
  const [tab, setTab] = useState<Tab>("requests");
  const requests = useAsync(() => admin.joinRequests(), []);
  const invites = useAsync(() => admin.invites(), []);
  const flats = useAsync(() => admin.flats(), []);
  const settings = useAsync(() => admin.communitySettings(), []);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [newFlat, setNewFlat] = useState("");
  const [newBlock, setNewBlock] = useState("");
  const [reopenHrs, setReopenHrs] = useState("");
  const [savingGate, setSavingGate] = useState(false);

  async function saveSettings(body: { reopenWindowHours?: number; requireAllocationApproval?: boolean }) {
    setSavingGate(true);
    setErr(null);
    try {
      await admin.updateCommunitySettings(body);
      settings.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setSavingGate(false);
    }
  }

  useFocusEffect(
    useCallback(() => {
      requests.refresh();
      invites.refresh();
      flats.refresh();
      settings.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  async function decide(r: JoinRequestView, approve: boolean) {
    setBusyId(r.id);
    setErr(null);
    try {
      approve ? await admin.approveJoin(r.id) : await admin.rejectJoin(r.id);
      requests.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusyId(null);
    }
  }

  async function makeInvite(flatId?: string) {
    setErr(null);
    try {
      await admin.createInvite({ flatId, maxUses: flatId ? 1 : 20, validDays: 30 });
      invites.reload();
      setTab("invites");
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  async function addFlat() {
    setErr(null);
    try {
      await admin.createFlat({ block: newBlock.trim() || undefined, flatNumber: newFlat.trim() });
      setNewFlat("");
      setNewBlock("");
      flats.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  return (
    <Screen
      onRefresh={() => {
        requests.refresh();
        invites.refresh();
        flats.refresh();
      }}
      refreshing={requests.refreshing}
    >
      <AppText size="xl" weight="700">
        Community
      </AppText>
      <Segmented
        value={tab}
        onChange={setTab}
        options={[
          { value: "requests", label: `Requests${(requests.data?.length ?? 0) ? ` (${requests.data!.length})` : ""}` },
          { value: "invites", label: "Invites" },
          { value: "flats", label: "Flats" },
          { value: "settings", label: "Settings" },
        ]}
      />
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      {tab === "requests" &&
        (requests.loading ? (
          <Loading />
        ) : (requests.data?.length ?? 0) === 0 ? (
          <EmptyState title="No pending requests" body="Residents who join with an invite code are added automatically." />
        ) : (
          requests.data!.map((r) => (
            <Card key={r.id} style={{ gap: theme.space(2) }}>
              <AppText weight="700">{r.userName ?? "Resident"}</AppText>
              <AppText size="sm" tone="muted">
                {r.userPhoneMasked} · wants {r.requestedFlatLabel ?? "a flat"}
              </AppText>
              <View style={{ flexDirection: "row", gap: theme.space(2) }}>
                <Button label="Approve" fullWidth={false} loading={busyId === r.id} onPress={() => decide(r, true)} />
                <Button label="Reject" variant="danger" fullWidth={false} loading={busyId === r.id} onPress={() => decide(r, false)} />
              </View>
            </Card>
          ))
        ))}

      {tab === "invites" && (
        <>
          <Button label="＋ Create a community invite code" onPress={() => makeInvite()} />
          <Divider />
          {invites.loading ? (
            <Loading />
          ) : (invites.data?.length ?? 0) === 0 ? (
            <EmptyState title="No invite codes" />
          ) : (
            invites.data!.map((c: InviteView) => (
              <Card key={c.id} style={{ gap: theme.space(1) }}>
                <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                  <AppText size="lg" weight="700" style={{ letterSpacing: 2 }}>
                    {c.code}
                  </AppText>
                  <Pill text={c.status} tone={c.status === "ACTIVE" ? "success" : "muted"} />
                </View>
                <AppText size="xs" tone="faint">
                  used {c.useCount}/{c.maxUses}
                  {c.flatId ? " · flat-specific" : " · community-wide"}
                </AppText>
              </Card>
            ))
          )}
        </>
      )}

      {tab === "settings" &&
        (settings.loading || !settings.data ? (
          <Loading />
        ) : (
          <>
            <Card style={{ gap: theme.space(2) }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                <View style={{ flex: 1, paddingRight: theme.space(3) }}>
                  <AppText weight="700">Resident approves the helper</AppText>
                  <AppText size="xs" tone="faint">
                    When on, an assigned ticket waits for the resident to approve the helper before that
                    helper is notified.
                  </AppText>
                </View>
                <Switch
                  value={settings.data.requireAllocationApproval}
                  disabled={savingGate}
                  onValueChange={(v) => saveSettings({ requireAllocationApproval: v })}
                />
              </View>
            </Card>

            <Card style={{ gap: theme.space(2) }}>
              <AppText weight="700">Reopen window</AppText>
              <AppText size="xs" tone="faint">
                Residents can reopen a resolved ticket for this many hours. Currently{" "}
                {settings.data.reopenWindowHours}h.
              </AppText>
              <View style={{ flexDirection: "row", gap: theme.space(2), alignItems: "center" }}>
                <View style={{ flex: 1 }}>
                  <Field
                    placeholder={`${settings.data.reopenWindowHours}`}
                    keyboardType="number-pad"
                    value={reopenHrs}
                    onChangeText={setReopenHrs}
                  />
                </View>
                <Button
                  label="Save"
                  fullWidth={false}
                  loading={savingGate}
                  disabled={!(Number(reopenHrs) > 0)}
                  onPress={() => {
                    saveSettings({ reopenWindowHours: Number(reopenHrs) });
                    setReopenHrs("");
                  }}
                />
              </View>
            </Card>

            <Card style={{ gap: theme.space(2) }}>
              <AppText weight="700">Ticket categories</AppText>
              <Pill
                text={settings.data.categoryAdmin === "COMMUNITY" ? "Managed by this community" : "Managed by the platform"}
                tone={settings.data.categoryAdmin === "COMMUNITY" ? "primary" : "muted"}
              />
              {settings.data.categoryAdmin === "COMMUNITY" ? (
                <CommunityCategoryEditor theme={theme} onError={setErr} />
              ) : (
                <AppText size="xs" tone="faint">
                  The platform team manages this community's ticket categories.
                </AppText>
              )}
            </Card>
          </>
        ))}

      {tab === "flats" && (
        <>
          <Card style={{ gap: theme.space(2) }}>
            <AppText size="sm" weight="700">
              Add a flat
            </AppText>
            <View style={{ flexDirection: "row", gap: theme.space(2) }}>
              <View style={{ flex: 1 }}>
                <Field placeholder="Block" value={newBlock} onChangeText={setNewBlock} />
              </View>
              <View style={{ flex: 1 }}>
                <Field placeholder="Number" value={newFlat} onChangeText={setNewFlat} />
              </View>
            </View>
            <Button label="Add flat" onPress={addFlat} disabled={newFlat.trim().length === 0} />
          </Card>
          {flats.loading ? (
            <Loading />
          ) : (
            (flats.data ?? []).map((f: FlatView) => (
              <Pressable key={f.id} onPress={() => makeInvite(f.id)}>
                <Card style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                  <View>
                    <AppText weight="700">{f.label}</AppText>
                    <AppText size="xs" tone="faint">
                      {f.occupancyType.toLowerCase().replace(/_/g, " ")}
                    </AppText>
                  </View>
                  <AppText size="xs" tone="primary">
                    Invite →
                  </AppText>
                </Card>
              </Pressable>
            ))
          )}
        </>
      )}
    </Screen>
  );
}

const REQ_TYPES = ["ISSUE", "FEEDBACK", "ENQUIRY"] as const;

function CommunityCategoryEditor({ theme, onError }: { theme: any; onError: (m: string | null) => void }) {
  const cats = useAsync(() => adminCategories.list(), []);
  const [name, setName] = useState("");
  const [reqType, setReqType] = useState<(typeof REQ_TYPES)[number]>("ISSUE");
  const [busy, setBusy] = useState(false);

  async function run(fn: () => Promise<any>) {
    setBusy(true);
    onError(null);
    try {
      await fn();
      cats.reload();
    } catch (e: any) {
      onError(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={{ gap: theme.space(2) }}>
      <Field placeholder="New category name" value={name} onChangeText={setName} />
      <View style={{ flexDirection: "row", gap: theme.space(2) }}>
        {REQ_TYPES.map((rt) => (
          <Button
            key={rt}
            label={rt}
            fullWidth={false}
            variant={reqType === rt ? "primary" : "secondary"}
            onPress={() => setReqType(rt)}
          />
        ))}
      </View>
      <Button
        label="Add category"
        loading={busy}
        disabled={!name.trim()}
        onPress={() => run(async () => {
          await adminCategories.create({ name: name.trim(), requestType: reqType });
          setName("");
        })}
      />
      {(cats.data ?? []).map((c: TicketCategory) => (
        <View key={c.id} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <AppText size="sm" style={{ opacity: c.active ? 1 : 0.5 }}>
            {c.name} · {c.requestType}
          </AppText>
          <AppText
            size="xs"
            tone={c.active ? "danger" : "primary"}
            onPress={() => run(() => (c.active ? adminCategories.deactivate(c.id) : adminCategories.reactivate(c.id)))}
          >
            {c.active ? "Deactivate" : "Reactivate"}
          </AppText>
        </View>
      ))}
    </View>
  );
}
