import { api } from "../api";
import { useAsync } from "../hooks";
import type { TenantHealth } from "../types";
import { Table } from "../ui";

export function Communities() {
  const health = useAsync(() => api.get<TenantHealth[]>("/superadmin/tenants"), []);

  return (
    <>
      <div className="page-head">
        <h1>Communities</h1>
        <span className="faint">{(health.data ?? []).length} active</span>
      </div>
      {health.error && <p className="error">{health.error.message}</p>}
      {health.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={health.data ?? []}
          cols={[
            { head: "Name", cell: (t) => <strong>{t.name}</strong> },
            { head: "City", cell: (t) => t.city ?? "—" },
            { head: "Open tickets", cell: (t) => t.openTickets },
            { head: "Total tickets", cell: (t) => t.totalTickets },
          ]}
          empty="No active communities."
        />
      )}
      <p className="faint" style={{ marginTop: 16 }}>
        Suspend / archive and branding edits stay in the Expo Super Admin screens for v1.
      </p>
    </>
  );
}
