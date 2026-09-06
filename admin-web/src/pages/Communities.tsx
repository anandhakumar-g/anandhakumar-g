import { useState } from "react";
import { ApiError, api, downloadCsv } from "../api";
import { useAsync } from "../hooks";
import type { TenantHealth } from "../types";
import { Button, Table } from "../ui";

export function Communities() {
  const health = useAsync(() => api.get<TenantHealth[]>("/superadmin/tenants"), []);
  const [downloading, setDownloading] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);

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

  return (
    <>
      <div className="page-head">
        <h1>Communities</h1>
        <div className="row">
          <span className="faint">{(health.data ?? []).length} active</span>
          <Button variant="secondary" loading={downloading} onClick={exportCsv}>Download CSV</Button>
        </div>
      </div>
      {csvError && <p className="error">{csvError}</p>}
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
