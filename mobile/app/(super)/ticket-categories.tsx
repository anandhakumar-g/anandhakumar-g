import React, { useMemo, useState } from "react";
import { View } from "react-native";
import { superCategories, superadmin } from "@/api/endpoints";
import { TicketCategory } from "@/api/types";
import { Divider, EmptyState, Pill, Segmented } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

const REQUEST_TYPES = ["ISSUE", "FEEDBACK", "ENQUIRY"] as const;

export default function TicketCategories() {
  const { theme } = useTheme();
  const tenants = useAsync(() => superadmin.tenantHealth(), []);
  const [scope, setScope] = useState<string>("GLOBAL"); // "GLOBAL" | tenantId
  const tenantId = scope === "GLOBAL" ? undefined : scope;
  const cats = useAsync(() => superCategories.list(tenantId), [scope]);

  const [name, setName] = useState("");
  const [reqType, setReqType] = useState<(typeof REQUEST_TYPES)[number]>("ISSUE");
  const [sla, setSla] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function run(fn: () => Promise<any>) {
    setBusy(true);
    setErr(null);
    try {
      await fn();
      cats.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  const scopeOptions = useMemo(
    () => [
      { value: "GLOBAL", label: "Global" },
      ...(tenants.data ?? []).map((t) => ({ value: t.id, label: t.name })),
    ],
    [tenants.data]
  );

  return (
    <Screen onRefresh={() => { tenants.refresh(); cats.refresh(); }} refreshing={cats.refreshing}>
      <AppText size="xxl" weight="700">Ticket categories</AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        The global catalogue applies to every community. Pick a community to manage its own list.
      </AppText>
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}

      <Segmented value={scope} onChange={setScope} options={scopeOptions} />

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">
          New category {scope === "GLOBAL" ? "(global)" : ""}
        </AppText>
        <Field label="Name" value={name} onChangeText={setName} placeholder="Fire Safety" />
        <View style={{ flexDirection: "row", gap: theme.space(2) }}>
          {REQUEST_TYPES.map((rt) => (
            <Button
              key={rt}
              label={rt}
              fullWidth={false}
              variant={reqType === rt ? "primary" : "secondary"}
              onPress={() => setReqType(rt)}
            />
          ))}
        </View>
        <Field label="SLA hours (optional)" keyboardType="number-pad" value={sla} onChangeText={setSla} />
        <Button
          label="Add category"
          loading={busy}
          disabled={!name.trim()}
          onPress={() => run(async () => {
            await superCategories.create(
              { name: name.trim(), requestType: reqType, slaHours: Number(sla) || undefined },
              tenantId
            );
            setName(""); setSla("");
          })}
        />
      </Card>

      <Divider />
      {cats.loading ? (
        <Loading />
      ) : (cats.data?.length ?? 0) === 0 ? (
        <EmptyState title="No categories in this scope" />
      ) : (
        (cats.data ?? []).map((c: TicketCategory) => (
          <Card key={c.id} style={{ gap: theme.space(1), opacity: c.active ? 1 : 0.55 }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700">{c.name}</AppText>
              <AppText
                size="sm"
                tone={c.active ? "danger" : "primary"}
                onPress={() => run(() =>
                  c.active
                    ? superCategories.deactivate(c.id, tenantId)
                    : superCategories.reactivate(c.id, tenantId)
                )}
              >
                {c.active ? "Deactivate" : "Reactivate"}
              </AppText>
            </View>
            <View style={{ flexDirection: "row", gap: theme.space(1.5) }}>
              <Pill text={c.requestType} />
              {c.slaHours ? <Pill text={`SLA ${c.slaHours}h`} tone="primary" /> : null}
              {!c.active ? <Pill text="inactive" /> : null}
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
