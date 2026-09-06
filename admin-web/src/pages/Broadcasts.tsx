import { useState } from "react";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { BroadcastRow, Page, TenantHealth } from "../types";
import { Button, Card, Field, Pill, Select, Table, TextArea } from "../ui";

const SCOPES = [
  { value: "ALL_ADMINS", label: "All community admins" },
  { value: "ALL_USERS", label: "All app users" },
  { value: "COMMUNITY", label: "One community" },
];

export function Broadcasts() {
  const history = useAsync(() => api.get<Page<BroadcastRow>>("/superadmin/broadcasts", { query: { size: 50 } }), []);
  const tenants = useAsync(() => api.get<TenantHealth[]>("/superadmin/tenants"), []);

  const [scope, setScope] = useState("ALL_ADMINS");
  const [tenantId, setTenantId] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [scheduledFor, setScheduledFor] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const needsTenant = scope === "COMMUNITY";
  const canSend = title.trim().length > 0 && body.trim().length > 0 && (!needsTenant || !!tenantId);

  async function send() {
    if (!canSend) {
      setError("Title, message, and (for one community) a community are all required.");
      return;
    }
    setSending(true);
    setError(null);
    setSent(null);
    try {
      const b = await api.post<BroadcastRow>("/superadmin/broadcasts", {
        scope,
        tenantId: needsTenant ? tenantId : undefined,
        title: title.trim(),
        body: body.trim(),
        scheduledFor: scheduledFor ? new Date(scheduledFor).toISOString() : undefined,
      });
      setSent(
        b.status === "PENDING"
          ? `Scheduled for ${new Date(b.scheduledFor!).toLocaleString()}.`
          : `Sent to ${b.recipientCount} recipient${b.recipientCount === 1 ? "" : "s"}.`,
      );
      setTitle("");
      setBody("");
      setScheduledFor("");
      history.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSending(false);
    }
  }

  async function cancel(id: string) {
    setBusyId(id);
    setError(null);
    try {
      await api.del("/superadmin/broadcasts/" + id);
      history.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  const rows = history.data?.content ?? [];

  return (
    <>
      <div className="page-head">
        <h1>Announcements</h1>
        <span className="faint">{history.data?.totalElements ?? 0} sent</span>
      </div>

      <Card title="Compose">
        <div className="stack">
          <div className="row">
            <Select label="Audience" value={scope} onChange={(e) => setScope(e.target.value)}>
              {SCOPES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            {needsTenant && (
              <Select label="Community" value={tenantId} onChange={(e) => setTenantId(e.target.value)}>
                <option value="">Choose…</option>
                {(tenants.data ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </Select>
            )}
          </div>
          <Field label="Title" value={title} maxLength={160} onChange={(e) => setTitle(e.target.value)} />
          <TextArea label="Message" value={body} maxLength={2000} onChange={(e) => setBody(e.target.value)} />
          <Field
            label="Send at (optional — leave blank to send now)"
            type="datetime-local"
            value={scheduledFor}
            onChange={(e) => setScheduledFor(e.target.value)}
          />
          {error && <p className="error">{error}</p>}
          {sent && <p className="ok">{sent}</p>}
          <div>
            <Button loading={sending} disabled={!canSend} onClick={send}>
              Send announcement
            </Button>
          </div>
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        {history.loading ? (
          <p className="faint">Loading…</p>
        ) : (
          <Table
            rows={rows}
            empty="No announcements yet."
            cols={[
              { head: "When", cell: (r) => new Date(r.at).toLocaleString() },
              { head: "Audience", cell: (r) => <Pill text={r.scope} tone="accent" /> },
              { head: "Community", cell: (r) => r.tenantName ?? "—" },
              { head: "Sender", cell: (r) => `${r.senderName ?? "—"}${r.senderRole ? ` (${r.senderRole})` : ""}` },
              { head: "Title", cell: (r) => <strong>{r.title}</strong> },
              { head: "Recipients", cell: (r) => (r.status === "PENDING" ? "—" : r.recipientCount) },
              {
                head: "Status",
                cell: (r) => (
                  <Pill
                    text={r.status === "PENDING" && r.scheduledFor ? `Sends ${new Date(r.scheduledFor).toLocaleString()}` : r.status}
                    tone={r.status === "SENT" ? "success" : r.status === "CANCELLED" ? "muted" : "warning"}
                  />
                ),
              },
              {
                head: "",
                cell: (r) => (
                  <span style={{ display: "inline-flex", gap: 8 }}>
                    <button className="btn link" onClick={() => setOpenId(openId === r.id ? null : r.id)}>
                      {openId === r.id ? "Hide" : "Read"}
                    </button>
                    {r.status === "PENDING" && (
                      <button className="btn link" disabled={busyId === r.id} onClick={() => cancel(r.id)}>
                        {busyId === r.id ? "Cancelling…" : "Cancel"}
                      </button>
                    )}
                  </span>
                ),
              },
            ]}
          />
        )}
        {openId && rows.find((r) => r.id === openId) && (
          <Card title={rows.find((r) => r.id === openId)!.title}>
            <p style={{ whiteSpace: "pre-wrap" }}>{rows.find((r) => r.id === openId)!.body}</p>
          </Card>
        )}
      </div>
    </>
  );
}
