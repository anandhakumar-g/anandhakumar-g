import { TicketView } from "@/api/types";

const OPEN_BEFORE_WORK = ["NEW", "ACKNOWLEDGED", "PENDING_RESIDENT_APPROVAL", "ASSIGNED", "ACCEPTED"];
const CLOSED = ["RESOLVED", "CLOSED"];

/** A short SLA badge for a ticket, or null when there's nothing to show. */
export function slaBadge(t: TicketView): { text: string; tone: "danger" | "primary" } | null {
  if (CLOSED.includes(t.status)) return null;
  if (t.slaBreachedAt) {
    return { text: `SLA breached${overdue(t.slaBreachedAt)}`, tone: "danger" };
  }
  if (t.slaDueAt && new Date(t.slaDueAt).getTime() < Date.now()) {
    return { text: `Overdue${overdue(t.slaDueAt)}`, tone: "danger" };
  }
  if (t.slaDueAt && OPEN_BEFORE_WORK.includes(t.status)) {
    return { text: `Due ${new Date(t.slaDueAt).toLocaleDateString()}`, tone: "primary" };
  }
  return null;
}

function overdue(since: string): string {
  const hrs = Math.floor((Date.now() - new Date(since).getTime()) / 3_600_000);
  if (hrs < 1) return "";
  return hrs < 48 ? ` · ${hrs}h` : ` · ${Math.floor(hrs / 24)}d`;
}
