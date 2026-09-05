import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { CommunityRequest, Page } from "../types";
import { Button, Table } from "../ui";

export function CommunityRequests() {
  const q = useAsync(
    () => api.get<Page<CommunityRequest>>("/superadmin/community-requests", { query: { size: 50 } }),
    []
  );
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function act(tenantId: string, kind: "approve" | "reject") {
    setErr(null);
    let body: unknown = {};
    if (kind === "reject") {
      const reason = window.prompt("Reason for rejecting? (optional)") ?? "";
      body = { reason };
    }
    setBusy(tenantId + kind);
    try {
      await api.post(`/superadmin/community-requests/${tenantId}/${kind}`, body);
      await q.reload();
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  const rows = q.data?.content ?? [];

  return (
    <>
      <div className="page-head">
        <h1>Community requests</h1>
        <span className="faint">{rows.length} pending</span>
      </div>
      {err && <p className="error">{err}</p>}
      {q.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={rows}
          empty="No communities are awaiting review."
          cols={[
            { head: "Community", cell: (r) => <strong>{r.name}</strong> },
            { head: "Where", cell: (r) => [r.locality, r.city].filter(Boolean).join(", ") || "—" },
            {
              head: "Requested by",
              cell: (r) => (
                <>
                  {r.requestedByName ?? "—"}
                  <br />
                  <span className="faint">{r.requestedByPhoneMasked ?? ""}</span>
                </>
              ),
            },
            { head: "When", cell: (r) => new Date(r.requestedAt).toLocaleDateString() },
            {
              head: "",
              cell: (r) => (
                <div className="row">
                  <Button loading={busy === r.tenantId + "approve"} onClick={() => act(r.tenantId, "approve")}>
                    Approve
                  </Button>
                  <Button
                    variant="danger"
                    loading={busy === r.tenantId + "reject"}
                    onClick={() => act(r.tenantId, "reject")}
                  >
                    Reject
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
    </>
  );
}
