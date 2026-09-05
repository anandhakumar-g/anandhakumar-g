import { Link } from "react-router-dom";
import { api } from "../api";
import { useAsync } from "../hooks";
import type { AnalyticsView, CommunityRequest, Page, TenantHealth } from "../types";
import { BarChart } from "../ui/Chart";
import { Card, Stat } from "../ui";

function weekLabel(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function Dashboard() {
  const a = useAsync(() => api.get<AnalyticsView>("/superadmin/analytics", { query: { bucket: "WEEK", points: 12 } }), []);
  const health = useAsync(() => api.get<TenantHealth[]>("/superadmin/tenants"), []);
  const reqs = useAsync(
    () => api.get<Page<CommunityRequest>>("/superadmin/community-requests", { query: { size: 1 } }),
    []
  );

  const t = a.data?.totals;
  const series = a.data?.series ?? [];
  const pendingCount = reqs.data?.totalElements ?? 0;

  return (
    <>
      <div className="page-head"><h1>Dashboard</h1></div>

      {pendingCount > 0 && (
        <div className="card" style={{ borderColor: "var(--warning)", marginBottom: 16 }}>
          <strong>{pendingCount}</strong> {pendingCount === 1 ? "community is" : "communities are"} awaiting review.{" "}
          <Link to="/communities/requests">Open the queue →</Link>
        </div>
      )}

      {a.error && <p className="error">{a.error.message}</p>}

      <div className="stats" style={{ marginBottom: 20 }}>
        <Stat k="Communities (active / total)" v={t ? `${t.activeCommunities} / ${t.communities}` : "…"} />
        <Stat k="Residents" v={t ? t.residents : "…"} />
        <Stat k="Providers" v={t ? t.providers : "…"} />
        <Stat k="Open tickets" v={t ? t.openTickets : "…"} />
        <Stat k="MRR" v={t ? `₹${t.mrr}` : "…"} />
      </div>

      <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))" }}>
        <Card title="Tickets created / week">
          <BarChart data={series.map((p) => ({ x: weekLabel(p.periodStart), y: p.ticketsCreated }))} />
        </Card>
        <Card title="New users / week">
          <BarChart data={series.map((p) => ({ x: weekLabel(p.periodStart), y: p.newUsers }))} />
        </Card>
        <Card title="Offers redeemed / week">
          <BarChart data={series.map((p) => ({ x: weekLabel(p.periodStart), y: p.offersRedeemed }))} />
        </Card>
        <Card title="Revenue / week (₹)">
          <BarChart data={series.map((p) => ({ x: weekLabel(p.periodStart), y: p.revenue }))} />
        </Card>
      </div>

      <Card title="Communities" actions={<Link to="/communities">All →</Link>}>
        {health.loading ? (
          <p className="faint">Loading…</p>
        ) : (
          <p className="faint">{(health.data ?? []).length} active communities.</p>
        )}
      </Card>
    </>
  );
}
