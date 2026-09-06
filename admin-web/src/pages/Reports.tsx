import { useState } from "react";
import { ApiError, downloadCsv } from "../api";
import { Button, Card, Field } from "../ui";

/** A YYYY-MM-DD date input → an ISO-8601 instant at UTC midnight, or undefined. */
function isoStart(d: string): string | undefined {
  return d ? new Date(`${d}T00:00:00Z`).toISOString() : undefined;
}

type Key = "tickets" | "offers" | "communities";

export function Reports() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState<Key | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(key: Key, path: string, filtered: boolean) {
    setBusy(key);
    setError(null);
    try {
      await downloadCsv(
        path,
        filtered ? { from: isoStart(from), to: isoStart(to), status: status.trim() || undefined } : undefined,
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : (e as Error).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>Reports</h1>
        <span className="faint">Platform-wide CSV exports</span>
      </div>

      <Card title="Filters">
        <div className="row">
          <Field label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <Field label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          <Field
            label="Status (optional)"
            placeholder="e.g. CLOSED"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          />
        </div>
        <p className="faint" style={{ marginTop: 10 }}>
          From / to bound the row's created date; status matches the row's status exactly. Communities
          ignores all three.
        </p>
      </Card>

      {error && <p className="error" style={{ marginTop: 12 }}>{error}</p>}

      <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", marginTop: 16 }}>
        <Card title="Tickets">
          <p className="faint">Every ticket across all communities, one row each.</p>
          <Button
            style={{ marginTop: 12 }}
            loading={busy === "tickets"}
            onClick={() => run("tickets", "/superadmin/exports/tickets.csv", true)}
          >
            Download CSV
          </Button>
        </Card>

        <Card title="Offers">
          <p className="faint">Every offer with its discount, coupon, and validity window.</p>
          <Button
            style={{ marginTop: 12 }}
            loading={busy === "offers"}
            onClick={() => run("offers", "/superadmin/exports/offers.csv", true)}
          >
            Download CSV
          </Button>
        </Card>

        <Card title="Communities">
          <p className="faint">Every community with its city, status, and ticket counts.</p>
          <Button
            style={{ marginTop: 12 }}
            variant="secondary"
            loading={busy === "communities"}
            onClick={() => run("communities", "/superadmin/exports/communities.csv", false)}
          >
            Download CSV
          </Button>
        </Card>
      </div>
    </>
  );
}
