import {
  Building2,
  CreditCard,
  FileText,
  Gauge,
  Inbox,
  Layers,
  type LucideIcon,
  Megaphone,
  ScrollText,
  Tag,
  Wrench,
} from "lucide-react";
import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import { Button, DensityToggle } from "./ui";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Communities } from "./pages/Communities";
import { CommunityRequests } from "./pages/CommunityRequests";
import { Providers } from "./pages/Providers";
import { Offers } from "./pages/Offers";
import { Billing } from "./pages/Billing";
import { Reports } from "./pages/Reports";
import { Taxonomy } from "./pages/Taxonomy";
import { Audit } from "./pages/Audit";
import { Broadcasts } from "./pages/Broadcasts";

const NAV: { to: string; label: string; icon: LucideIcon; end?: boolean }[] = [
  { to: "/", label: "Dashboard", icon: Gauge, end: true },
  { to: "/communities", label: "Communities", icon: Building2 },
  { to: "/communities/requests", label: "Requests", icon: Inbox },
  { to: "/providers", label: "Providers", icon: Wrench },
  { to: "/offers", label: "Offers", icon: Tag },
  { to: "/taxonomy", label: "Taxonomy", icon: Layers },
  { to: "/broadcasts", label: "Announcements", icon: Megaphone },
  { to: "/audit", label: "Audit", icon: ScrollText },
  { to: "/billing", label: "Billing", icon: CreditCard },
  { to: "/reports", label: "Reports", icon: FileText },
];

function Shell({ children }: { children: React.ReactNode }) {
  const { me, signOut } = useAuth();
  return (
    <div className="layout">
      <nav className="nav">
        <div className="brand">Single Point</div>
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => (isActive ? "active" : "")}>
            <n.icon size={16} strokeWidth={1.75} aria-hidden />
            {n.label}
          </NavLink>
        ))}
        <div className="spacer" />
        <DensityToggle />
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
        <Route path="/taxonomy" element={<Taxonomy />} />
        <Route path="/broadcasts" element={<Broadcasts />} />
        <Route path="/audit" element={<Audit />} />
        <Route path="/billing" element={<Billing />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
}
