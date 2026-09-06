import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { InvoiceView, PlanView, SubscriptionView } from "../types";
import { Button, Card, Field, Pill, Select, Table } from "../ui";

type EntRow = { key: string; value: string };

function entToRows(e: Record<string, number>): EntRow[] {
  return Object.entries(e).map(([key, value]) => ({ key, value: String(value) }));
}
function rowsToEnt(rows: EntRow[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const r of rows) {
    const k = r.key.trim();
    if (k) out[k] = Number(r.value || 0);
  }
  return out;
}

function EntitlementEditor({ rows, onChange }: { rows: EntRow[]; onChange: (r: EntRow[]) => void }) {
  return (
    <div className="stack" style={{ gap: 8 }}>
      <label style={{ fontSize: 12, fontWeight: 600, color: "var(--text-muted)" }}>Entitlements</label>
      {rows.map((r, i) => (
        <div className="row" key={i}>
          <input
            placeholder="KEY e.g. TICKETS_PER_MONTH"
            value={r.key}
            onChange={(e) => onChange(rows.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))}
            style={{ flex: 2 }}
          />
          <input
            type="number"
            placeholder="-1 = unlimited"
            value={r.value}
            onChange={(e) => onChange(rows.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
            style={{ flex: 1 }}
          />
          <Button variant="link" onClick={() => onChange(rows.filter((_, j) => j !== i))}>
            Remove
          </Button>
        </div>
      ))}
      <div>
        <Button variant="secondary" onClick={() => onChange([...rows, { key: "", value: "0" }])}>
          Add entitlement
        </Button>
      </div>
    </div>
  );
}

function CreatePlanForm({ onDone }: { onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState("TENANT");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [billingCycle, setBillingCycle] = useState("MONTHLY");
  const [price, setPrice] = useState("0");
  const [rows, setRows] = useState<EntRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await api.post("/superadmin/plans", {
        target,
        code: code.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        billingCycle,
        price: Number(price || 0),
        entitlements: rowsToEnt(rows),
      });
      setOpen(false);
      setCode("");
      setName("");
      setDescription("");
      setPrice("0");
      setRows([]);
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!open)
    return (
      <Button variant="secondary" onClick={() => setOpen(true)}>
        New plan
      </Button>
    );

  return (
    <Card title="New plan">
      <div className="stack">
        <div className="row">
          <Select label="For" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="TENANT">Community</option>
            <option value="PROVIDER">Vendor</option>
          </Select>
          <Select label="Cycle" value={billingCycle} onChange={(e) => setBillingCycle(e.target.value)}>
            <option value="MONTHLY">Monthly</option>
            <option value="ANNUAL">Annual</option>
          </Select>
          <Field label="Code" placeholder="TENANT_PRO" value={code} onChange={(e) => setCode(e.target.value)} />
          <Field label="Price" type="number" value={price} onChange={(e) => setPrice(e.target.value)} />
        </div>
        <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <Field label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
        <EntitlementEditor rows={rows} onChange={setRows} />
        {error && <p className="error">{error}</p>}
        <div className="row">
          <Button loading={busy} disabled={!code.trim() || !name.trim()} onClick={submit}>
            Create plan
          </Button>
          <Button variant="secondary" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </div>
    </Card>
  );
}

function EditPlanForm({ plan, onDone, onCancel }: { plan: PlanView; onDone: () => void; onCancel: () => void }) {
  const [name, setName] = useState(plan.name);
  const [description, setDescription] = useState(plan.description ?? "");
  const [price, setPrice] = useState(String(plan.price));
  const [active, setActive] = useState(plan.active);
  const [rows, setRows] = useState<EntRow[]>(entToRows(plan.entitlements));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await api.put(`/superadmin/plans/${plan.id}`, {
        name: name.trim(),
        description: description.trim() || undefined,
        price: Number(price || 0),
        entitlements: rowsToEnt(rows),
        active,
      });
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title={`Edit ${plan.code}`}>
      <div className="stack">
        <div className="row">
          <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Field label="Price" type="number" value={price} onChange={(e) => setPrice(e.target.value)} />
          <Select label="Visible" value={active ? "yes" : "no"} onChange={(e) => setActive(e.target.value === "yes")}>
            <option value="yes">Sellable</option>
            <option value="no">Hidden</option>
          </Select>
        </div>
        <Field label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
        <EntitlementEditor rows={rows} onChange={setRows} />
        {error && <p className="error">{error}</p>}
        <div className="row">
          <Button loading={busy} onClick={submit}>
            Save plan
          </Button>
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </div>
    </Card>
  );
}

export function Billing() {
  const plans = useAsync(() => api.get<PlanView[]>("/superadmin/plans"), []);
  const subs = useAsync(() => api.get<SubscriptionView[]>("/superadmin/subscriptions"), []);
  const invoices = useAsync(() => api.get<InvoiceView[]>("/superadmin/invoices"), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [editId, setEditId] = useState<string | null>(null);

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

  const editing = plans.data?.find((p) => p.id === editId) ?? null;

  return (
    <>
      <div className="page-head"><h1>Billing</h1></div>
      {err && <p className="error">{err}</p>}

      <div className="stack">
        <Card title="Plans" actions={<CreatePlanForm onDone={plans.reload} />}>
          <Table
            rows={plans.data ?? []}
            cols={[
              { head: "Code", cell: (p) => <strong>{p.code}</strong> },
              { head: "For", cell: (p) => p.target },
              { head: "Price", cell: (p) => `₹${p.price} / ${p.billingCycle.toLowerCase()}` },
              {
                head: "Status",
                cell: (p) => (
                  <>
                    {p.isDefault && <Pill text="default" tone="accent" />}{" "}
                    {!p.active && <Pill text="hidden" tone="muted" />}
                  </>
                ),
              },
              {
                head: "",
                cell: (p) => (
                  <button className="btn link" onClick={() => setEditId(editId === p.id ? null : p.id)}>
                    {editId === p.id ? "Close" : "Edit"}
                  </button>
                ),
              },
            ]}
          />
        </Card>

        {editing && (
          <EditPlanForm
            plan={editing}
            onCancel={() => setEditId(null)}
            onDone={() => {
              setEditId(null);
              plans.reload();
            }}
          />
        )}

        <Card title="Subscriptions">
          <Table
            rows={subs.data ?? []}
            empty="No subscriptions."
            cols={[
              { head: "Subject", cell: (s) => `${s.subjectType} · ${s.subjectId.slice(0, 8)}` },
              {
                head: "Status",
                cell: (s) => (
                  <>
                    <Pill text={s.status} tone={s.status === "ACTIVE" || s.status === "COMPED" ? "success" : s.status === "EXPIRED" || s.status === "CANCELLED" ? "danger" : "warning"} />{" "}
                    {s.gatewaySubscriptionId && <Pill text="recurring" tone="accent" />}
                  </>
                ),
              },
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
