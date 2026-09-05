import type { ReactNode } from "react";
import { Card } from "../ui";

/** Placeholder pages — filled in over MVP-11 checkpoints 5–8. */
function Stub({ title, note }: { title: string; note: ReactNode }) {
  return (
    <>
      <div className="page-head"><h1>{title}</h1></div>
      <Card>
        <p className="faint">{note}</p>
      </Card>
    </>
  );
}

export const StubPage = Stub;
