import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import { Button } from "./ui";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Communities } from "./pages/Communities";
import { CommunityRequests } from "./pages/CommunityRequests";
import { Providers } from "./pages/Providers";
import { Offers } from "./pages/Offers";
import { Billing } from "./pages/Billing";
import { Reports } from "./pages/Reports";

const NAV = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/communities", label: "Communities" },
  { to: "/communities/requests", label: "Requests" },
  { to: "/providers", label: "Providers" },
  { to: "/offers", label: "Offers" },
  { to: "/billing", label: "Billing" },
  { to: "/reports", label: "Reports" },
];

function Shell({ children }: { children: React.ReactNode }) {
  const { me, signOut } = useAuth();
  return (
    <div className="layout">
      <nav className="nav">
        <div className="brand">Single Point · Console</div>
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => (isActive ? "active" : "")}>
            {n.label}
          </NavLink>
        ))}
        <div className="spacer" />
        <div className="faint" style={{ padding: "0 12px 8px", fontSize: 12 }}>{me?.name ?? "Super Admin"}</div>
        <Button variant="secondary" onClick={signOut}>Sign out</Button>
      </nav>
      <main className="main">{children}</main>
    </div>
  );
}

export function App() {
  const { ready, me } = useAuth();
  if (!ready) return <div className="login-wrap"><p className="muted">Loading…</p></div>;
  if (!me) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }
  return (
    <Shell>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/communities" element={<Communities />} />
        <Route path="/communities/requests" element={<CommunityRequests />} />
        <Route path="/providers" element={<Providers />} />
        <Route path="/offers" element={<Offers />} />
        <Route path="/billing" element={<Billing />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
}
