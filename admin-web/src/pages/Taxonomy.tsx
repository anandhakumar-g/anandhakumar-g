import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { TicketCategoryRow, VendorCategoryRow, VendorKind } from "../types";
import { Button, Card, Field, Pill, Select, Table } from "../ui";

const REQUEST_TYPES = ["ISSUE", "FEEDBACK", "ENQUIRY"];

export function Taxonomy() {
  return (
    <>
      <div className="page-head">
        <h1>Taxonomy</h1>
        <span className="faint">Vendor verticals, vendor categories, global ticket categories</span>
      </div>
      <div className="stack">
        <Verticals />
        <VendorCategories />
        <TicketCategories />
      </div>
    </>
  );
}

function useMutate(reload: () => void) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  async function run(key: string, fn: () => Promise<unknown>) {
    setBusy(key);
    setError(null);
    try {
      await fn();
      reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  }
  return { busy, error, run };
}

function Verticals() {
  const q = useAsync(() => api.get<VendorKind[]>("/superadmin/vendor-category-kinds"), []);
  const { busy, error, run } = useMutate(q.reload);
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");

  return (
    <Card title="Vendor verticals">
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginBottom: 12 }}>
        <Field label="Code" placeholder="HOME_SERVICES" value={code} onChange={(e) => setCode(e.target.value)} />
        <Field label="Label" placeholder="Home services" value={label} onChange={(e) => setLabel(e.target.value)} />
        <Button
          disabled={!code.trim() || !label.trim()}
          loading={busy === "create"}
          onClick={() =>
            run("create", async () => {
              await api.post("/superadmin/vendor-category-kinds", { code: code.trim(), label: label.trim() });
              setCode("");
              setLabel("");
            })
          }
        >
          Add vertical
        </Button>
      </div>
      {q.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={q.data ?? []}
          empty="No verticals."
          cols={[
            { head: "Code", cell: (k) => <code>{k.code}</code> },
            { head: "Label", cell: (k) => k.label },
            { head: "Order", cell: (k) => k.sortOrder },
            { head: "Active", cell: (k) => <Pill text={k.active ? "active" : "inactive"} tone={k.active ? "success" : "muted"} /> },
            {
              head: "",
              cell: (k) => (
                <div className="row">
                  <Button
                    variant="link"
                    onClick={() => {
                      const label = window.prompt("New label", k.label);
                      if (label && label.trim()) run("r" + k.id, () => api.put(`/superadmin/vendor-category-kinds/${k.id}`, { label: label.trim() }));
                    }}
                  >
                    Rename
                  </Button>
                  {k.active && (
                    <Button
                      variant="link"
                      loading={busy === "d" + k.id}
                      onClick={() => run("d" + k.id, () => api.post(`/superadmin/vendor-category-kinds/${k.id}/deactivate`))}
                    >
                      Deactivate
                    </Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}

function VendorCategories() {
  const q = useAsync(() => api.get<VendorCategoryRow[]>("/superadmin/vendor-categories"), []);
  const kinds = useAsync(() => api.get<VendorKind[]>("/superadmin/vendor-category-kinds"), []);
  const { busy, error, run } = useMutate(q.reload);
  const [name, setName] = useState("");
  const [kind, setKind] = useState("");
  const activeKinds = (kinds.data ?? []).filter((k) => k.active);

  return (
    <Card title="Vendor categories">
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginBottom: 12 }}>
        <Field label="Name" placeholder="Electrician" value={name} onChange={(e) => setName(e.target.value)} />
        <Select label="Vertical" value={kind} onChange={(e) => setKind(e.target.value)}>
          <option value="">Choose…</option>
          {activeKinds.map((k) => (
            <option key={k.id} value={k.code}>
              {k.label}
            </option>
          ))}
        </Select>
        <Button
          disabled={!name.trim() || !kind}
          loading={busy === "create"}
          onClick={() =>
            run("create", async () => {
              await api.post("/superadmin/vendor-categories", { name: name.trim(), kind });
              setName("");
              setKind("");
            })
          }
        >
          Add category
        </Button>
      </div>
      {q.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={q.data ?? []}
          empty="No vendor categories."
          cols={[
            { head: "Name", cell: (c) => <strong>{c.name}</strong> },
            { head: "Vertical", cell: (c) => <code>{c.kind}</code> },
            { head: "Order", cell: (c) => c.sortOrder },
            { head: "Active", cell: (c) => <Pill text={c.active ? "active" : "inactive"} tone={c.active ? "success" : "muted"} /> },
            {
              head: "",
              cell: (c) => (
                <div className="row">
                  <Button
                    variant="link"
                    onClick={() => {
                      const name = window.prompt("New name", c.name);
                      if (name && name.trim()) run("r" + c.id, () => api.put(`/superadmin/vendor-categories/${c.id}`, { name: name.trim() }));
                    }}
                  >
                    Rename
                  </Button>
                  <Button
                    variant="link"
                    loading={busy === "t" + c.id}
                    onClick={() =>
                      run("t" + c.id, () =>
                        api.post(`/superadmin/vendor-categories/${c.id}/${c.active ? "deactivate" : "reactivate"}`),
                      )
                    }
                  >
                    {c.active ? "Deactivate" : "Reactivate"}
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}

function TicketCategories() {
  const q = useAsync(() => api.get<TicketCategoryRow[]>("/superadmin/ticket-categories"), []);
  const { busy, error, run } = useMutate(q.reload);
  const [name, setName] = useState("");
  const [requestType, setRequestType] = useState("ISSUE");
  const [slaHours, setSlaHours] = useState("");

  return (
    <Card title="Global ticket categories">
      <p className="faint" style={{ marginBottom: 12 }}>
        The platform catalogue every community inherits. Per-community lists stay in the Expo app.
      </p>
      {error && <p className="error">{error}</p>}
      <div className="row" style={{ marginBottom: 12 }}>
        <Field label="Name" placeholder="Plumbing" value={name} onChange={(e) => setName(e.target.value)} />
        <Select label="Request type" value={requestType} onChange={(e) => setRequestType(e.target.value)}>
          {REQUEST_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </Select>
        <Field
          label="SLA hours"
          type="number"
          placeholder="24"
          value={slaHours}
          onChange={(e) => setSlaHours(e.target.value)}
        />
        <Button
          disabled={!name.trim()}
          loading={busy === "create"}
          onClick={() =>
            run("create", async () => {
              await api.post("/superadmin/ticket-categories", {
                name: name.trim(),
                requestType,
                slaHours: slaHours ? Number(slaHours) : undefined,
              });
              setName("");
              setSlaHours("");
            })
          }
        >
          Add category
        </Button>
      </div>
      {q.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={q.data ?? []}
          empty="No global ticket categories."
          cols={[
            { head: "Name", cell: (c) => <strong>{c.name}</strong> },
            { head: "Type", cell: (c) => <code>{c.requestType}</code> },
            { head: "SLA", cell: (c) => (c.slaHours != null ? `${c.slaHours}h` : "—") },
            { head: "Order", cell: (c) => c.sortOrder },
            { head: "Active", cell: (c) => <Pill text={c.active ? "active" : "inactive"} tone={c.active ? "success" : "muted"} /> },
            {
              head: "",
              cell: (c) => (
                <div className="row">
                  <Button
                    variant="link"
                    onClick={() => {
                      const name = window.prompt("New name", c.name);
                      if (name && name.trim()) run("r" + c.id, () => api.put(`/superadmin/ticket-categories/${c.id}`, { name: name.trim() }));
                    }}
                  >
                    Rename
                  </Button>
                  <Button
                    variant="link"
                    loading={busy === "t" + c.id}
                    onClick={() =>
                      run("t" + c.id, () =>
                        api.post(`/superadmin/ticket-categories/${c.id}/${c.active ? "deactivate" : "reactivate"}`),
                      )
                    }
                  >
                    {c.active ? "Deactivate" : "Reactivate"}
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}
