import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { OfferView } from "../types";
import { Button, Pill, Table } from "../ui";

function discount(o: OfferView): string {
  return o.discountType === "PERCENTAGE" ? `${o.discountValue}% off` : `₹${o.discountValue} off`;
}

export function Offers() {
  const list = useAsync(() => api.get<OfferView[]>("/superadmin/offers", { query: { status: "PENDING_APPROVAL" } }), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function act(id: string, kind: "approve" | "reject") {
    setErr(null);
    let body: unknown = {};
    if (kind === "reject") body = { reason: window.prompt("Reason for rejecting?") ?? "not suitable" };
    setBusy(id + kind);
    try {
      await api.post(`/superadmin/offers/${id}/${kind}`, body);
      await list.reload();
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  const rows = list.data ?? [];

  return (
    <>
      <div className="page-head"><h1>Offers awaiting approval</h1><span className="faint">{rows.length}</span></div>
      {err && <p className="error">{err}</p>}
      {list.loading ? (
        <p className="faint">Loading…</p>
      ) : (
        <Table
          rows={rows}
          empty="Nothing in the approval queue."
          cols={[
            { head: "Offer", cell: (o) => <strong>{o.title}</strong> },
            { head: "Discount", cell: (o) => discount(o) },
            { head: "Audience", cell: (o) => <Pill text={o.target?.summary ?? "pending"} tone="accent" /> },
            {
              head: "Valid",
              cell: (o) => `${new Date(o.validFrom).toLocaleDateString()} – ${new Date(o.validTo).toLocaleDateString()}`,
            },
            {
              head: "",
              cell: (o) => (
                <div className="row">
                  <Button loading={busy === o.id + "approve"} onClick={() => act(o.id, "approve")}>Approve</Button>
                  <Button variant="danger" loading={busy === o.id + "reject"} onClick={() => act(o.id, "reject")}>Reject</Button>
                </div>
              ),
            },
          ]}
        />
      )}
      <p className="faint" style={{ marginTop: 16 }}>
        Audience overrides (target a set of communities / named people) stay in the Expo approval screen for v1.
      </p>
    </>
  );
}
