import { useState } from "react";
import { ApiError, api, downloadCsv } from "../api";
import { useAsync } from "../hooks";
import type { TenantHealth } from "../types";
import { Button, Card, Field, Pill, Select, Table } from "../ui";

const STATUSES = ["ACTIVE", "SUSPENDED", "ARCHIVED", "PENDING_REVIEW"];

function statusTone(s: string): "success" | "warning" | "muted" | "danger" {
  return s === "ACTIVE" ? "success" : s === "SUSPENDED" ? "warning" : s === "PENDING_REVIEW" ? "muted" : "danger";
}

function BrandingDrawer({ tenant, onDone, onCancel }: { tenant: TenantHealth; onDone: () => void; onCancel: () => void }) {
  const [name, setName] = useState(tenant.name);
  const [brandPrimaryColor, setColor] = useState("");
  const [logoUrl, setLogoUrl] = useState("");
  const [defaultTheme, setTheme] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api.put(`/superadmin/tenants/${tenant.id}`, {
        name: name.trim() || undefined,
        brandPrimaryColor: brandPrimaryColor.trim() || undefined,
        logoUrl: logoUrl.trim() || undefined,
        defaultTheme: defaultTheme.trim() || undefined,
      });
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title={`Branding — ${tenant.name}`}>
      <div className="stack">
        <p className="faint">Blank fields are left unchanged.</p>
        <div className="row">
          <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Field label="Primary color" placeholder="#2E7D32" value={brandPrimaryColor} onChange={(e) => setColor(e.target.value)} />
          <Select label="Default theme" value={defaultTheme} onChange={(e) => setTheme(e.target.value)}>
            <option value="">Unchanged</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </Select>
        </div>
        <Field label="Logo URL" value={logoUrl} onChange={(e) => setLogoUrl(e.target.value)} />
        {error && <p className="error">{error}</p>}
        <div className="row">
          <Button loading={busy} onClick={save}>Save branding</Button>
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        </div>
      </div>
    </Card>
  );
}

export function Communities() {
  const [status, setStatus] = useState("ACTIVE");
  const health = useAsync(
    () => api.get<TenantHealth[]>("/superadmin/tenants", { query: { status } }),
    [status],
  );
  const [downloading, setDownloading] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [brandingId, setBrandingId] = useState<string | null>(null);

  async function exportCsv() {
    setDownloading(true);
    setCsvError(null);
    try {
      await downloadCsv("/superadmin/exports/communities.csv");
    } catch (e) {
      setCsvError(e instanceof ApiError ? e.message : (e as Error).message);
    } finally {
      setDownloading(false);
    }
  }

  async function setLifecycle(id: string, next: string) {
    setBusy(id + next);
    setActionError(null);
    try {
      await api.put(`/superadmin/tenants/${id}`, { status: next });
      await health.reload();
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  const branding = health.data?.find((t) => t.id === brandingId) ?? null;

  return (
    <>
      <div className="page-head">
        <h1>Communities</h1>
        <div className="row">
          <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replace(/_/g, " ")}
              </option>
            ))}
          </Select>
          <Button variant="secondary" loading={downloading} onClick={exportCsv}>Download CSV</Button>
        </div>
      </div>
      {csvError && <p className="error">{csvError}</p>}
      {actionError && <p className="error">{actionError}</p>}

      {health.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={health.data ?? []}
          empty={`No ${status.replace(/_/g, " ").toLowerCase()} communities.`}
          cols={[
            { head: "Name", cell: (t) => <strong>{t.name}</strong> },
            { head: "City", cell: (t) => t.city ?? "—" },
            { head: "Status", cell: (t) => <Pill text={t.status.replace(/_/g, " ")} tone={statusTone(t.status)} /> },
            { head: "Open", cell: (t) => t.openTickets },
            { head: "Total", cell: (t) => t.totalTickets },
            {
              head: "",
              cell: (t) => (
                <div className="row">
                  {t.status === "ACTIVE" && (
                    <>
                      <Button variant="secondary" loading={busy === t.id + "SUSPENDED"} onClick={() => setLifecycle(t.id, "SUSPENDED")}>Suspend</Button>
                      <Button variant="link" onClick={() => setBrandingId(brandingId === t.id ? null : t.id)}>Branding</Button>
                    </>
                  )}
                  {t.status === "SUSPENDED" && (
                    <Button loading={busy === t.id + "ACTIVE"} onClick={() => setLifecycle(t.id, "ACTIVE")}>Reactivate</Button>
                  )}
                  {t.status === "ARCHIVED" && (
                    <Button loading={busy === t.id + "ACTIVE"} onClick={() => setLifecycle(t.id, "ACTIVE")}>Reactivate</Button>
                  )}
                  {(t.status === "ACTIVE" || t.status === "SUSPENDED") && (
                    <Button variant="danger" loading={busy === t.id + "ARCHIVED"} onClick={() => setLifecycle(t.id, "ARCHIVED")}>Archive</Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}

      {branding && (
        <BrandingDrawer
          tenant={branding}
          onCancel={() => setBrandingId(null)}
          onDone={() => {
            setBrandingId(null);
            health.reload();
          }}
        />
      )}
    </>
  );
}
