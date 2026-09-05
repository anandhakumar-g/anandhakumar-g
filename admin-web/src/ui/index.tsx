import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

export function Button({
  variant = "primary",
  loading,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "link"; loading?: boolean }) {
  return (
    <button className={`btn ${variant}`} disabled={rest.disabled || loading} {...rest}>
      {loading ? "…" : children}
    </button>
  );
}

export function Card({ title, children, actions }: { title?: ReactNode; children: ReactNode; actions?: ReactNode }) {
  return (
    <div className="card">
      {(title || actions) && (
        <div className="page-head" style={{ marginBottom: 12 }}>
          {title ? <h2>{title}</h2> : <span />}
          {actions}
        </div>
      )}
      {children}
    </div>
  );
}

export function Field({
  label,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <div className="field">
      <label>{label}</label>
      <input {...rest} />
    </div>
  );
}

export function Select({
  label,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      <select {...rest}>{children}</select>
    </div>
  );
}

export function Pill({ text, tone = "muted" }: { text: string; tone?: "muted" | "success" | "danger" | "warning" | "accent" }) {
  return <span className={`pill ${tone}`}>{text}</span>;
}

export function Stat({ k, v }: { k: string; v: ReactNode }) {
  return (
    <div className="stat">
      <div className="v">{v}</div>
      <div className="k">{k}</div>
    </div>
  );
}

export function Table<T>({
  rows,
  cols,
  empty = "Nothing here.",
}: {
  rows: T[];
  cols: { head: string; cell: (row: T) => ReactNode }[];
  empty?: string;
}) {
  if (rows.length === 0) return <p className="faint">{empty}</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{cols.map((c) => <th key={c.head}>{c.head}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>{cols.map((c) => <td key={c.head}>{c.cell(r)}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
