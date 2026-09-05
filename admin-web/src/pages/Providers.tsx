import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { KycDoc, SuperProvider } from "../types";
import { Button, Pill, Table } from "../ui";

function verTone(s: string): "success" | "danger" | "warning" | "muted" {
  if (s === "VERIFIED") return "success";
  if (s === "REJECTED" || s === "SUSPENDED") return "danger";
  return "warning";
}

export function Providers() {
  const list = useAsync(() => api.get<SuperProvider[]>("/superadmin/providers"), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [openKyc, setOpenKyc] = useState<string | null>(null);

  async function run(key: string, fn: () => Promise<unknown>) {
    setErr(null);
    setBusy(key);
    try {
      await fn();
      await list.reload();
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="page-head"><h1>Providers</h1><span className="faint">{(list.data ?? []).length}</span></div>
      {err && <p className="error">{err}</p>}
      {list.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={list.data ?? []}
          cols={[
            {
              head: "Name",
              cell: (p) => (
                <>
                  <strong>{p.name}</strong>
                  {p.ratingCount > 0 && <span className="faint"> · ★ {p.ratingAvg?.toFixed(1)} ({p.ratingCount})</span>}
                </>
              ),
            },
            { head: "Status", cell: (p) => <Pill text={p.verificationStatus} tone={verTone(p.verificationStatus)} /> },
            { head: "Tier", cell: (p) => p.tier },
            {
              head: "",
              cell: (p) => (
                <div className="row">
                  {p.verificationStatus !== "VERIFIED" && (
                    <Button
                      loading={busy === p.id + "v"}
                      onClick={() => run(p.id + "v", () => api.post(`/superadmin/providers/${p.id}/verify`, { status: "VERIFIED" }))}
                    >
                      Verify
                    </Button>
                  )}
                  <Button
                    variant="secondary"
                    loading={busy === p.id + "t"}
                    onClick={() =>
                      run(p.id + "t", () =>
                        api.post(`/superadmin/providers/${p.id}/tier`, {
                          tier: p.tier === "FEATURED" ? "STANDARD" : "FEATURED",
                        })
                      )
                    }
                  >
                    {p.tier === "FEATURED" ? "Unfeature" : "Feature"}
                  </Button>
                  <Button variant="link" onClick={() => setOpenKyc(openKyc === p.id ? null : p.id)}>
                    {openKyc === p.id ? "Hide KYC" : "KYC"}
                  </Button>
                </div>
              ),
            },
          ]}
        />
      )}
      {openKyc && <KycPanel providerId={openKyc} />}
    </>
  );
}

function KycPanel({ providerId }: { providerId: string }) {
  const docs = useAsync(() => api.get<KycDoc[]>(`/superadmin/providers/${providerId}/kyc`), [providerId]);
  const [busy, setBusy] = useState<string | null>(null);

  async function review(docId: string, status: "ACCEPTED" | "REJECTED") {
    setBusy(docId + status);
    try {
      const note = status === "REJECTED" ? window.prompt("Rejection note?") ?? "" : "";
      await api.post(`/superadmin/providers/${providerId}/kyc/${docId}/review`, { status, note });
      await docs.reload();
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="card" style={{ marginTop: 14 }}>
      <h2>KYC documents</h2>
      <Table
        rows={docs.data ?? []}
        empty="No documents uploaded."
        cols={[
          { head: "Type", cell: (d) => d.type },
          { head: "File", cell: (d) => d.originalFilename ?? "—" },
          { head: "Status", cell: (d) => <Pill text={d.status} tone={d.status === "ACCEPTED" ? "success" : d.status === "REJECTED" ? "danger" : "warning"} /> },
          {
            head: "",
            cell: (d) =>
              d.status === "PENDING" || d.status === "SUBMITTED" ? (
                <div className="row">
                  <Button loading={busy === d.id + "ACCEPTED"} onClick={() => review(d.id, "ACCEPTED")}>Accept</Button>
                  <Button variant="danger" loading={busy === d.id + "REJECTED"} onClick={() => review(d.id, "REJECTED")}>Reject</Button>
                </div>
              ) : null,
          },
        ]}
      />
    </div>
  );
}
