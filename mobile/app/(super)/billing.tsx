import React, { useState } from "react";
import { View } from "react-native";
import { superBilling } from "@/api/endpoints";
import {
  InvoiceView, money, PlanView, SubjectType, SubscriptionStatus, SubscriptionView, subscriptionTone,
} from "@/api/types";
import { Divider, EmptyState, Pill, Segmented } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

const SUB_FILTERS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PAST_DUE", label: "Past due" },
  { value: "EXPIRED", label: "Expired" },
  { value: "ACTIVE", label: "Active" },
];

export default function SuperBilling() {
  const { theme } = useTheme();
  const plans = useAsync(() => superBilling.plans(), []);
  const providers = useAsync(() => superBilling.providers(), []);
  const [subFilter, setSubFilter] = useState("ALL");
  const subs = useAsync(
    () => superBilling.subscriptions(undefined, subFilter === "ALL" ? undefined : (subFilter as SubscriptionStatus)),
    [subFilter]
  );
  const dueInvoices = useAsync(() => superBilling.invoices("DUE"), []);

  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  // assign form
  const [subjectType, setSubjectType] = useState<SubjectType>("TENANT");
  const [subjectId, setSubjectId] = useState("");
  const [planCode, setPlanCode] = useState<string | null>(null);
  const [comp, setComp] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  function refreshAll() {
    subs.reload();
    dueInvoices.reload();
    providers.reload();
  }

  async function run(key: string, fn: () => Promise<any>) {
    setBusy(key);
    setErr(null);
    setMsg(null);
    try {
      await fn();
      refreshAll();
    } catch (e: any) {
      setErr(e?.message ?? "Failed");
    } finally {
      setBusy(null);
    }
  }

  const planOptions = (plans.data ?? []).filter((p) => p.target === subjectType && p.active);

  return (
    <Screen onRefresh={() => { plans.refresh(); refreshAll(); }} refreshing={subs.refreshing}>
      <AppText size="xxl" weight="700">Billing</AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Assign plans, settle invoices, and curate the featured directory tier.
      </AppText>
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}
      {msg ? <AppText tone="success" size="sm">{msg}</AppText> : null}

      {/* ---- assign a plan ---- */}
      <Card style={{ gap: theme.space(2.5) }}>
        <AppText size="sm" weight="700" tone="muted">Assign a plan</AppText>
        <Segmented
          value={subjectType}
          onChange={(v) => { setSubjectType(v as SubjectType); setPlanCode(null); }}
          options={[{ value: "TENANT", label: "Community" }, { value: "PROVIDER", label: "Vendor" }]}
        />
        <Field
          label={subjectType === "TENANT" ? "Community (tenant) ID" : "Provider ID"}
          value={subjectId}
          onChangeText={setSubjectId}
          autoCapitalize="none"
          placeholder="uuid"
        />
        <View style={{ gap: theme.space(1.5) }}>
          <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>PLAN</AppText>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
            {planOptions.map((p) => (
              <Button
                key={p.id}
                label={`${p.name}${p.price > 0 ? ` · ${money(p.price, p.currency)}` : ""}`}
                variant={planCode === p.code ? "primary" : "secondary"}
                fullWidth={false}
                onPress={() => setPlanCode(p.code)}
              />
            ))}
          </View>
        </View>
        <View style={{ flexDirection: "row", gap: theme.space(2), alignItems: "center" }}>
          <Button
            label={comp ? "Comp: on" : "Comp: off"}
            variant={comp ? "primary" : "ghost"}
            fullWidth={false}
            onPress={() => setComp((v) => !v)}
          />
          <AppText size="xs" tone="faint">Comp = free of charge, no invoice</AppText>
        </View>
        <Button
          label="Assign plan"
          loading={busy === "assign"}
          disabled={!subjectId.trim() || !planCode}
          onPress={() =>
            run("assign", async () => {
              await superBilling.assign({
                subjectType, subjectId: subjectId.trim(), planCode: planCode!, comp,
              });
              setSubjectId(""); setPlanCode(null); setComp(false);
              setMsg("Plan assigned.");
            })
          }
        />
      </Card>

      {/* ---- due invoices ---- */}
      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">Due invoices</AppText>
        {dueInvoices.loading ? (
          <Loading />
        ) : (dueInvoices.data?.length ?? 0) === 0 ? (
          <AppText size="sm" tone="faint">Nothing outstanding.</AppText>
        ) : (
          dueInvoices.data!.map((inv: InvoiceView) => (
            <View key={inv.id} style={{ gap: theme.space(1) }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
                <View style={{ flexShrink: 1 }}>
                  <AppText size="sm" weight="700">{money(inv.amount, inv.currency)}</AppText>
                  <AppText size="xs" tone="faint">
                    {inv.subjectType} · {inv.subjectId.slice(0, 8)}…
                  </AppText>
                </View>
                <Button
                  label="Mark paid"
                  variant="secondary"
                  fullWidth={false}
                  loading={busy === "mp" + inv.id}
                  onPress={() => run("mp" + inv.id, () => superBilling.markPaid(inv.id))}
                />
              </View>
              <Divider />
            </View>
          ))
        )}
      </Card>

      {/* ---- subscriptions ---- */}
      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">Subscriptions</AppText>
        <Segmented value={subFilter} onChange={setSubFilter} options={SUB_FILTERS} />
        {subs.loading ? (
          <Loading />
        ) : (subs.data?.length ?? 0) === 0 ? (
          <AppText size="sm" tone="faint">None.</AppText>
        ) : (
          subs.data!.map((s: SubscriptionView) => (
            <View key={s.id} style={{ gap: theme.space(1.5) }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                <AppText size="sm" weight="700">{s.planName ?? s.planCode ?? "—"}</AppText>
                <Pill text={s.status.replace(/_/g, " ")} tone={subscriptionTone(s.status)} />
              </View>
              <AppText size="xs" tone="faint">
                {s.subjectType} · {s.subjectId.slice(0, 8)}…
                {s.currentPeriodEnd
                  ? ` · ends ${new Date(s.currentPeriodEnd).toLocaleDateString(undefined, { month: "short", day: "numeric" })}`
                  : ""}
              </AppText>
              {s.status !== "CANCELLED" ? (
                <View style={{ flexDirection: "row", gap: theme.space(2) }}>
                  {s.status !== "COMPED" ? (
                    <Button label="Comp" variant="ghost" fullWidth={false}
                      loading={busy === "comp" + s.id}
                      onPress={() => run("comp" + s.id, () => superBilling.comp(s.id))} />
                  ) : null}
                  <Button label="Cancel" variant="danger" fullWidth={false}
                    loading={busy === "cancel" + s.id}
                    onPress={() => run("cancel" + s.id, () => superBilling.cancel(s.id))} />
                </View>
              ) : null}
              <Divider />
            </View>
          ))
        )}
      </Card>

      {/* ---- featured tier ---- */}
      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">Featured vendors</AppText>
        {providers.loading ? (
          <Loading />
        ) : (providers.data?.length ?? 0) === 0 ? (
          <EmptyState title="No vendors yet" />
        ) : (
          providers.data!.map((p) => (
            <View key={p.id} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
              <View style={{ flexShrink: 1 }}>
                <AppText size="sm" weight="600">{p.name}</AppText>
                <AppText size="xs" tone="faint">{p.verificationStatus.replace(/_/g, " ")}</AppText>
              </View>
              <Button
                label={p.tier === "FEATURED" ? "★ Featured" : "Make featured"}
                variant={p.tier === "FEATURED" ? "primary" : "secondary"}
                fullWidth={false}
                loading={busy === "tier" + p.id}
                onPress={() =>
                  run("tier" + p.id, () =>
                    superBilling.setTier(p.id, p.tier === "FEATURED" ? "STANDARD" : "FEATURED")
                  )
                }
              />
            </View>
          ))
        )}
      </Card>

      {/* ---- plan catalogue ---- */}
      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">Plan catalogue</AppText>
        {plans.loading ? (
          <Loading />
        ) : (
          (["TENANT", "PROVIDER"] as SubjectType[]).map((target) => (
            <View key={target} style={{ gap: theme.space(1.5) }}>
              <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
                {target === "TENANT" ? "COMMUNITY" : "VENDOR"}
              </AppText>
              {(plans.data ?? []).filter((p) => p.target === target).map((p: PlanView) => (
                <View key={p.id} style={{ gap: theme.space(0.5) }}>
                  <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                    <AppText size="sm" weight="600">
                      {p.name} {p.isDefault ? "· default" : ""}
                    </AppText>
                    <AppText size="sm" tone="muted">
                      {p.price > 0 ? `${money(p.price, p.currency)}/${p.billingCycle.toLowerCase()}` : "Free"}
                    </AppText>
                  </View>
                  <AppText size="xs" tone="faint">
                    {Object.entries(p.entitlements)
                      .map(([k, v]) => `${k.toLowerCase().replace(/_/g, " ")}: ${v < 0 ? "∞" : v}`)
                      .join("  ·  ")}
                  </AppText>
                </View>
              ))}
            </View>
          ))
        )}
      </Card>
    </Screen>
  );
}
