/** Dependency-free inline-SVG bar chart for the analytics series. */
export function BarChart({
  data,
  label,
  height = 120,
}: {
  data: { x: string; y: number }[];
  label?: string;
  height?: number;
}) {
  const max = Math.max(1, ...data.map((d) => d.y));
  const w = 100 / Math.max(1, data.length);
  return (
    <div>
      {label && <div className="faint" style={{ fontSize: 12, marginBottom: 4 }}>{label}</div>}
      <svg viewBox={`0 0 100 ${height}`} preserveAspectRatio="none" style={{ width: "100%", height }}>
        {data.map((d, i) => {
          const h = (d.y / max) * (height - 16);
          return (
            <g key={i}>
              <rect
                x={i * w + w * 0.15}
                y={height - h - 12}
                width={w * 0.7}
                height={Math.max(h, 0.5)}
                rx="1"
                fill="var(--primary)"
              />
              <text
                x={i * w + w / 2}
                y={height - 2}
                fontSize="4"
                textAnchor="middle"
                fill="var(--text-faint)"
              >
                {d.x}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
