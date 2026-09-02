import * as WebBrowser from "expo-web-browser";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { billing } from "@/api/endpoints";
import {
  featureLabel, InvoiceView, money, MyBillingView, PlanView, subscriptionTone,
} from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";
import { Divider, Pill } from "./Bits";
import { Button } from "./Button";
import { AppText, Card, Loading } from "./Themed";

/** Community-plan (admin) or listing-plan (provider) card for the Settings screen. */
export function BillingCard() {
  const { theme } = useTheme();
  const [data, setData] = useState<MyBillingView | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const pollRef = useRef<any>(null);

  const load = useCallback(async () => {
    try {
      setData(await billing.mine());
    } catch (e: any) {
      setErr(e?.message ?? "Could not load your plan");
      setData(null);
    }
  }, []);

  useEffect(() => {
    load();
    return () => clearInterval(pollRef.current);
  }, [load]);

  async function run(fn: () => Promise<any>) {
    setBusy(true);
    setErr(null);
    try {
      await fn();
      await load();
    } catch (e: any) {
      setErr(e?.message ?? "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  async function payInvoice(inv: InvoiceView) {
    setErr(null);
    try {
      const { paymentLink } = await billing.payInvoice(inv.id);
      if (paymentLink) await WebBrowser.openBrowserAsync(paymentLink);
    } catch (e: any) {
      setErr(e?.message ?? "Could not open the payment page");
    }
    // poll a few times for the webhook to land
    clearInterval(pollRef.current);
    let n = 0;
    pollRef.current = setInterval(() => {
      n += 1;
      load();
      if (n >= 10) clearInterval(pollRef.current);
    }, 3000);
  }

  if (data === undefined) {
    return (
      <Card>
        <Loading />
      </Card>
    );
  }
  if (!data) {
    return (
      <Card style={{ gap: theme.space(1) }}>
        <AppText size="sm" weight="700" tone="muted">Plan</AppText>
        <AppText size="sm" tone="danger">{err ?? "Unavailable"}</AppText>
      </Card>
    );
  }

  const isProvider = data.subjectType === "PROVIDER";
  const renewsOn = data.subscription?.currentPeriodEnd
    ? new Date(data.subscription.currentPeriodEnd).toLocaleDateString(undefined, {
        year: "numeric", month: "short", day: "numeric",
      })
    : null;
  const graceOn = data.subscription?.graceUntil
    ? new Date(data.subscription.graceUntil).toLocaleDateString(undefined, { month: "short", day: "numeric" })
    : null;

  return (
    <Card style={{ gap: theme.space(3) }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="sm" weight="700" tone="muted">
          {isProvider ? "Listing plan" : "Community plan"}
        </AppText>
        {data.subscription ? (
          <Pill
            text={data.subscription.status.replace(/_/g, " ")}
            tone={subscriptionTone(data.subscription.status)}
          />
        ) : (
          <Pill text="Free" tone="muted" />
        )}
      </View>

      <View style={{ gap: theme.space(0.5) }}>
        <AppText size="xl" weight="700" family="display">{data.plan.name}</AppText>
        {data.plan.price > 0 ? (
          <AppText size="sm" tone="muted">
            {money(data.plan.price, data.plan.currency)} / {data.plan.billingCycle.toLowerCase()}
          </AppText>
        ) : (
          <AppText size="sm" tone="muted">No charge</AppText>
        )}
        {renewsOn ? (
          <AppText size="xs" tone="faint">
            {data.subscription?.status === "EXPIRED" ? "Lapsed" : "Renews"} {renewsOn}
          </AppText>
        ) : null}
        {data.subscription?.status === "PAST_DUE" && graceOn ? (
          <AppText size="xs" tone="danger">Pay by {graceOn} to avoid going read-only</AppText>
        ) : null}
      </View>

      {isProvider ? (
        <View style={{ flexDirection: "row", alignItems: "center", gap: theme.space(2) }}>
          <AppText size="sm" tone="muted">Directory tier</AppText>
          <Pill
            text={data.providerTier === "FEATURED" ? "★ Featured" : "Standard"}
            tone={data.providerTier === "FEATURED" ? "primary" : "muted"}
          />
        </View>
      ) : null}

      <View style={{ gap: theme.space(2) }}>
        <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
          THIS MONTH
        </AppText>
        {data.usage.map((u) => (
          <UsageBar key={u.feature} label={featureLabel(u.feature)} used={u.used} limit={u.limit} />
        ))}
      </View>

      {data.dueInvoices.length > 0 ? (
        <>
          <Divider />
          <View style={{ gap: theme.space(2) }}>
            {data.dueInvoices.map((inv) => (
              <View
                key={inv.id}
                style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}
              >
                <View style={{ flexShrink: 1 }}>
                  <AppText size="sm" weight="700">{money(inv.amount, inv.currency)} due</AppText>
                  <AppText size="xs" tone="faint">
                    {new Date(inv.periodStart).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
                    {" – "}
                    {new Date(inv.periodEnd).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
                  </AppText>
                </View>
                <Button label="Pay" fullWidth={false} onPress={() => payInvoice(inv)} />
              </View>
            ))}
          </View>
        </>
      ) : null}

      {data.upgradeOptions.length > 0 ? (
        <>
          <Divider />
          <View style={{ gap: theme.space(2) }}>
            <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
              CHANGE PLAN
            </AppText>
            {data.upgradeOptions
              .filter((p) => p.code !== data.plan.code)
              .map((p) => (
                <PlanRow key={p.id} plan={p} busy={busy} onPick={() => run(() => billing.selfUpgrade(p.code))} />
              ))}
          </View>
        </>
      ) : null}

      {err ? <AppText size="sm" tone="danger">{err}</AppText> : null}
    </Card>
  );
}

function UsageBar({ label, used, limit }: { label: string; used: number; limit: number }) {
  const { theme } = useTheme();
  const unlimited = limit < 0;
  const disabled = limit === 0;
  const pct = unlimited || disabled ? 0 : Math.min(1, used / limit);
  const over = !unlimited && !disabled && used >= limit;

  return (
    <View style={{ gap: theme.space(1) }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
        <AppText size="sm">{label}</AppText>
        <AppText size="sm" tone={over ? "danger" : "muted"} weight="600">
          {unlimited ? `${used} · unlimited` : disabled ? "not included" : `${used} / ${limit}`}
        </AppText>
      </View>
      {!unlimited && !disabled ? (
        <View
          style={{
            height: 6, borderRadius: 3, overflow: "hidden",
            backgroundColor: theme.color.surfaceAlt,
          }}
        >
          <View
            style={{
              width: `${pct * 100}%`, height: "100%",
              backgroundColor: over ? theme.color.danger : theme.color.primary,
            }}
          />
        </View>
      ) : null}
    </View>
  );
}

function PlanRow({
  plan, busy, onPick,
}: {
  plan: PlanView; busy: boolean; onPick: () => void;
}) {
  const { theme } = useTheme();
  return (
    <View
      style={{
        flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2),
        borderWidth: 1, borderColor: theme.color.border, borderRadius: theme.radius.sm, padding: theme.space(3),
      }}
    >
      <View style={{ flexShrink: 1 }}>
        <AppText size="sm" weight="700">{plan.name}</AppText>
        <AppText size="xs" tone="faint">
          {plan.price > 0 ? `${money(plan.price, plan.currency)} / ${plan.billingCycle.toLowerCase()}` : "Free"}
        </AppText>
      </View>
      <Button
        label={plan.price > 0 ? "Choose" : "Switch"}
        variant="secondary"
        fullWidth={false}
        loading={busy}
        onPress={onPick}
      />
    </View>
  );
}
