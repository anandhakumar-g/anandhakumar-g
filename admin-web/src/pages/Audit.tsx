import { useMemo, useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { AuditLogRow, Page } from "../types";
import { Card, Field, Pill, Select, Table } from "../ui";

function isoStart(d: string): string | undefined {
  return d ? new Date(`${d}T00:00:00Z`).toISOString() : undefined;
}

export function Audit() {
  const [action, setAction] = useState("");
  const [success, setSuccess] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  const actions = useAsync(() => api.get<string[]>("/superadmin/audit-logs/actions"), []);
  const query = useMemo(
    () => ({
      action: action || undefined,
      success: success || undefined,
      from: isoStart(from),
      to: isoStart(to),
      page,
      size: 50,
    }),
    [action, success, from, to, page],
  );
  const logs = useAsync(
    () => api.get<Page<AuditLogRow>>("/superadmin/audit-logs", { query }),
    [action, success, from, to, page],
  );

  const rows = logs.data?.content ?? [];
  const totalPages = logs.data?.totalPages ?? 1;

  function resetTo(setter: (v: string) => void) {
    return (v: string) => {
      setter(v);
      setPage(0);
    };
  }

  return (
    <>
      <div className="page-head">
        <h1>Audit trail</h1>
        <span className="faint">{logs.data?.totalElements ?? 0} events</span>
      </div>

      <Card title="Filters">
        <div className="row">
          <Select label="Action" value={action} onChange={(e) => resetTo(setAction)(e.target.value)}>
            <option value="">Any action</option>
            {(actions.data ?? []).map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
          <Select label="Result" value={success} onChange={(e) => resetTo(setSuccess)(e.target.value)}>
            <option value="">Any result</option>
            <option value="true">Success</option>
            <option value="false">Failure</option>
          </Select>
          <Field label="From" type="date" value={from} onChange={(e) => resetTo(setFrom)(e.target.value)} />
          <Field label="To" type="date" value={to} onChange={(e) => resetTo(setTo)(e.target.value)} />
        </div>
      </Card>

      {logs.error && <p className="error">{logs.error.message}</p>}

      <div style={{ marginTop: 16 }}>
        {logs.loading ? (
          <p className="faint">Loading…</p>
        ) : (
          <Table
            rows={rows}
            empty="No matching audit events."
            cols={[
              { head: "When", cell: (r) => new Date(r.at).toLocaleString() },
              {
                head: "Actor",
                cell: (r) => (
                  <>
                    {r.actorName ?? "—"}
                    <br />
                    <span className="faint">
                      {[r.actorRole, r.actorPhoneMasked].filter(Boolean).join(" · ")}
                    </span>
                  </>
                ),
              },
              { head: "Community", cell: (r) => r.tenantName ?? "—" },
              { head: "Action", cell: (r) => <code>{r.action}</code> },
              {
                head: "Entity",
                cell: (r) => (r.entityType ? `${r.entityType}${r.entityId ? ` ${r.entityId.slice(0, 8)}` : ""}` : "—"),
              },
              {
                head: "Result",
                cell: (r) =>
                  r.success ? (
                    <Pill text="ok" tone="success" />
                  ) : (
                    <Pill text={r.errorCode ?? "failed"} tone="danger" />
                  ),
              },
              {
                head: "",
                cell: (r) => (
                  <button className="btn link" onClick={() => setOpenId(openId === r.id ? null : r.id)}>
                    {openId === r.id ? "Hide" : "Details"}
                  </button>
                ),
              },
            ]}
          />
        )}

        {openId && rows.find((r) => r.id === openId) && (
          <Card title="Event detail">
            {(() => {
              const r = rows.find((x) => x.id === openId)!;
              return (
                <table>
                  <tbody>
                    <tr><td className="faint">Request</td><td><code>{r.httpMethod} {r.endpoint}</code></td></tr>
                    <tr><td className="faint">Request id</td><td>{r.requestId ?? "—"}</td></tr>
                    <tr><td className="faint">Duration</td><td>{r.durationMs != null ? `${r.durationMs} ms` : "—"}</td></tr>
                    <tr><td className="faint">Entity id</td><td>{r.entityId ?? "—"}</td></tr>
                    <tr><td className="faint">Error code</td><td>{r.errorCode ?? "—"}</td></tr>
                    <tr><td className="faint">Detail</td><td style={{ whiteSpace: "pre-wrap" }}>{r.detail ?? "—"}</td></tr>
                  </tbody>
                </table>
              );
            })()}
          </Card>
        )}

        <div className="row" style={{ marginTop: 12, justifyContent: "flex-end" }}>
          <button className="btn secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span className="faint">
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <button className="btn secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </button>
        </div>
      </div>
    </>
  );
}
