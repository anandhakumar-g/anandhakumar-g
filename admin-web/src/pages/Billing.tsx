import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { InvoiceView, PlanView, SubscriptionView } from "../types";
import { Button, Card, Pill, Table } from "../ui";

export function Billing() {
  const plans = useAsync(() => api.get<PlanView[]>("/superadmin/plans"), []);
  const subs = useAsync(() => api.get<SubscriptionView[]>("/superadmin/subscriptions"), []);
  const invoices = useAsync(() => api.get<InvoiceView[]>("/superadmin/invoices"), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function run(key: string, fn: () => Promise<unknown>, reload: () => Promise<void>) {
    setErr(null);
    setBusy(key);
    try {
      await fn();
      await reload();
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="page-head"><h1>Billing</h1></div>
      {err && <p className="error">{err}</p>}

      <div className="stack">
        <Card title="Plans">
          <Table
            rows={plans.data ?? []}
            cols={[
              { head: "Code", cell: (p) => <strong>{p.code}</strong> },
              { head: "For", cell: (p) => p.target },
              { head: "Price", cell: (p) => `₹${p.priceAmount} / ${p.billingCycle.toLowerCase()}` },
              { head: "Default", cell: (p) => (p.isDefault ? <Pill text="default" tone="accent" /> : "") },
            ]}
          />
          <p className="faint" style={{ marginTop: 10 }}>Plan create / edit stays in the Expo Billing screen for v1.</p>
        </Card>

        <Card title="Subscriptions">
          <Table
            rows={subs.data ?? []}
            empty="No subscriptions."
            cols={[
              { head: "Subject", cell: (s) => `${s.subjectType} · ${s.subjectId.slice(0, 8)}` },
              { head: "Status", cell: (s) => <Pill text={s.status} tone={s.status === "ACTIVE" || s.status === "COMPED" ? "success" : s.status === "EXPIRED" || s.status === "CANCELLED" ? "danger" : "warning"} /> },
              { head: "Period end", cell: (s) => (s.currentPeriodEnd ? new Date(s.currentPeriodEnd).toLocaleDateString() : "—") },
              {
                head: "",
                cell: (s) =>
                  s.status !== "CANCELLED" ? (
                    <div className="row">
                      <Button variant="secondary" loading={busy === s.id + "comp"} onClick={() => run(s.id + "comp", () => api.post(`/superadmin/subscriptions/${s.id}/comp`), subs.reload)}>Comp</Button>
                      <Button variant="danger" loading={busy === s.id + "cancel"} onClick={() => run(s.id + "cancel", () => api.post(`/superadmin/subscriptions/${s.id}/cancel`), subs.reload)}>Cancel</Button>
                    </div>
                  ) : null,
              },
            ]}
          />
        </Card>

        <Card title="Invoices">
          <Table
            rows={invoices.data ?? []}
            empty="No invoices."
            cols={[
              { head: "Amount", cell: (i) => `₹${i.amount}` },
              { head: "Status", cell: (i) => <Pill text={i.status} tone={i.status === "PAID" ? "success" : i.status === "VOID" ? "muted" : "warning"} /> },
              { head: "Period", cell: (i) => `${new Date(i.periodStart).toLocaleDateString()} – ${new Date(i.periodEnd).toLocaleDateString()}` },
              {
                head: "",
                cell: (i) =>
                  i.status === "DUE" ? (
                    <Button loading={busy === i.id} onClick={() => run(i.id, () => api.post(`/superadmin/invoices/${i.id}/mark-paid`), invoices.reload)}>Mark paid</Button>
                  ) : null,
              },
            ]}
          />
        </Card>
      </div>
    </>
  );
}
