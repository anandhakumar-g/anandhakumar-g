import React from "react";
import { Pressable, View } from "react-native";
import { useTheme } from "@/theme/ThemeProvider";
import { statusColor } from "@/theme/tokens";
import { AppText } from "./Themed";

/** A tinted chip with a leading dot — background is the semantic colour at low alpha. */
function Chip({ label, color, tint }: { label: string; color: string; tint: string }) {
  const { theme } = useTheme();
  return (
    <View
      style={{
        alignSelf: "flex-start",
        flexDirection: "row",
        alignItems: "center",
        gap: theme.space(1.5),
        backgroundColor: tint,
        borderRadius: theme.radius.pill,
        paddingHorizontal: theme.space(2.5),
        paddingVertical: theme.space(1),
      }}
    >
      <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: color }} />
      <AppText size="xs" weight="600" style={{ color, letterSpacing: 0.2 }}>
        {label}
      </AppText>
    </View>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const { theme } = useTheme();
  const c = statusColor(theme, status);
  return <Chip label={status.replace(/_/g, " ")} color={c} tint={c + "1F"} />;
}

export function Pill({ text, tone = "muted" }: { text: string; tone?: "muted" | "primary" | "success" | "danger" }) {
  const { theme } = useTheme();
  const c =
    tone === "primary" ? theme.color.primary
    : tone === "success" ? theme.color.success
    : tone === "danger" ? theme.color.danger
    : theme.color.textMuted;
  const tint = tone === "muted" ? theme.color.surfaceAlt : c + "1F";
  return <Chip label={text} color={c} tint={tint} />;
}

export function EmptyState({ title, body, action }: { title: string; body?: string; action?: React.ReactNode }) {
  const { theme } = useTheme();
  return (
    <View style={{ alignItems: "center", gap: theme.space(2), paddingVertical: theme.space(10) }}>
      <AppText size="lg" weight="700">
        {title}
      </AppText>
      {body ? (
        <AppText tone="muted" style={{ textAlign: "center", maxWidth: 320 }}>
          {body}
        </AppText>
      ) : null}
      {action ? <View style={{ marginTop: theme.space(2) }}>{action}</View> : null}
    </View>
  );
}

export function Divider() {
  const { theme } = useTheme();
  return <View style={{ height: 1, backgroundColor: theme.color.border, marginVertical: theme.space(2) }} />;
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  const { theme } = useTheme();
  return (
    <View
      style={{
        flexDirection: "row",
        backgroundColor: theme.color.surfaceAlt,
        borderRadius: theme.radius.md,
        padding: theme.space(1),
        gap: theme.space(1),
      }}
    >
      {options.map((o) => {
        const active = o.value === value;
        return (
          <Pressable
            key={o.value}
            onPress={() => onChange(o.value)}
            style={{
              flex: 1,
              paddingVertical: theme.space(2),
              borderRadius: theme.radius.sm,
              backgroundColor: active ? theme.color.surface : "transparent",
              alignItems: "center",
            }}
          >
            <AppText size="sm" weight={active ? "700" : "500"} tone={active ? "default" : "muted"}>
              {o.label}
            </AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Stars({ value, onChange, size = 32 }: { value: number; onChange?: (v: number) => void; size?: number }) {
  const { theme } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: theme.space(1.5) }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Pressable key={n} onPress={onChange ? () => onChange(n) : undefined} disabled={!onChange}>
          <AppText style={{ fontSize: size, color: n <= value ? theme.color.warning : theme.color.textFaint }}>
            {n <= value ? "★" : "☆"}
          </AppText>
        </Pressable>
      ))}
    </View>
  );
}

export function KeyValue({ k, v }: { k: string; v?: string | null }) {
  const { theme } = useTheme();
  if (!v) return null;
  return (
    <View style={{ flexDirection: "row", justifyContent: "space-between", gap: theme.space(4), paddingVertical: theme.space(1) }}>
      <AppText size="sm" tone="muted">
        {k}
      </AppText>
      <AppText size="sm" weight="500" style={{ flexShrink: 1, textAlign: "right" }}>
        {v}
      </AppText>
    </View>
  );
}
