import { Stack } from "expo-router";
import React, { useMemo, useState } from "react";
import { Pressable, View } from "react-native";
import { audit as auditApi, communities } from "@/api/endpoints";
import type { AuditLogView } from "@/api/types";
import { Divider, EmptyState, Pill } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

type SuccessFilter = "ALL" | "OK" | "FAIL";
type RangeFilter = "1d" | "7d" | "30d" | "all";

const RANGE_MS: Record<RangeFilter, number | null> = {
  "1d": 86_400_000,
  "7d": 7 * 86_400_000,
  "30d": 30 * 86_400_000,
  all: null,
};

function since(range: RangeFilter): string | undefined {
  const span = RANGE_MS[range];
  return span == null ? undefined : new Date(Date.now() - span).toISOString();
}

function ago(iso: string): string {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return `${Math.floor(s)}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}

export default function AuditLog() {
  const { theme } = useTheme();
  const [action, setAction] = useState("");
  const [success, setSuccess] = useState<SuccessFilter>("ALL");
  const [range, setRange] = useState<RangeFilter>("7d");
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [pickCommunity, setPickCommunity] = useState(false);
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<string | null>(null);

  const query = useMemo(
    () => ({
      action: action.trim() || undefined,
      tenantId: tenantId || undefined,
      success: success === "ALL" ? undefined : success === "OK",
      from: since(range),
      page,
      size: 40,
    }),
    [action, tenantId, success, range, page]
  );

  const list = useAsync(() => auditApi.list(query), [JSON.stringify(query)]);
  const rows = list.data?.content ?? [];
  const totalPages = list.data?.totalPages ?? 1;

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <Stack.Screen options={{ title: "Audit log", headerShown: true }} />

      <AppText size="xl" weight="700">
        Audit log
      </AppText>
      <AppText tone="muted" size="sm">
        Every state-changing admin / provider call and every PII read. Read-only.
      </AppText>

      <Card style={{ gap: theme.space(2) }}>
        <Field
          label="Action contains"
          placeholder="e.g. Tenant, Offer, Broadcast"
          value={action}
          onChangeText={(v) => {
            setPage(0);
            setAction(v);
          }}
          autoCapitalize="none"
        />
        <Seg
          label="Outcome"
          options={[
            { value: "ALL", label: "All" },
            { value: "OK", label: "OK" },
            { value: "FAIL", label: "Failed" },
          ]}
          value={success}
          onChange={(v) => {
            setPage(0);
            setSuccess(v as SuccessFilter);
          }}
        />
        <Seg
          label="Since"
          options={[
            { value: "1d", label: "24h" },
            { value: "7d", label: "7d" },
            { value: "30d", label: "30d" },
            { value: "all", label: "All" },
          ]}
          value={range}
          onChange={(v) => {
            setPage(0);
            setRange(v as RangeFilter);
          }}
        />
        <Pressable onPress={() => setPickCommunity((v) => !v)}>
          <AppText size="sm" tone="primary">
            {tenantName ? `Community: ${tenantName} · change` : "Filter by community"}
          </AppText>
        </Pressable>
        {tenantId ? (
          <AppText
            size="xs"
            tone="danger"
            onPress={() => {
              setPage(0);
              setTenantId(null);
              setTenantName(null);
            }}
          >
            Clear community filter
          </AppText>
        ) : null}
        {pickCommunity ? (
          <CommunityPicker
            onPick={(id, name) => {
              setPage(0);
              setTenantId(id);
              setTenantName(name);
              setPickCommunity(false);
            }}
          />
        ) : null}
      </Card>

      {list.loading ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState title="No matching entries" body="Widen the time range or clear a filter." />
      ) : (
        rows.map((r) => <Row key={r.id} row={r} expanded={open === r.id} onToggle={() => setOpen(open === r.id ? null : r.id)} />)
      )}

      {totalPages > 1 ? (
        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: theme.space(2) }}>
          <AppText size="sm" tone={page > 0 ? "primary" : "faint"} onPress={() => page > 0 && setPage(page - 1)}>
            ‹ Newer
          </AppText>
          <AppText size="xs" tone="faint">
            {page + 1} / {totalPages}
          </AppText>
          <AppText
            size="sm"
            tone={page + 1 < totalPages ? "primary" : "faint"}
            onPress={() => page + 1 < totalPages && setPage(page + 1)}
          >
            Older ›
          </AppText>
        </View>
      ) : null}
    </Screen>
  );
}

function Seg({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: string; label: string }[];
  value: string;
  onChange: (v: string) => void;
}) {
  const { theme } = useTheme();
  return (
    <View style={{ gap: theme.space(1) }}>
      <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
        {label.toUpperCase()}
      </AppText>
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
    </View>
  );
}

function CommunityPicker({ onPick }: { onPick: (id: string, name: string) => void }) {
  const { theme } = useTheme();
  const [q, setQ] = useState("");
  const results = useAsync(() => communities.search(q.trim() || undefined), [q]);
  return (
    <View style={{ gap: theme.space(1) }}>
      <Field placeholder="Search communities" value={q} onChangeText={setQ} autoCapitalize="none" />
      {results.loading ? (
        <Loading />
      ) : (
        (results.data?.content ?? []).slice(0, 8).map((t) => (
          <Pressable key={t.id} onPress={() => onPick(t.id, t.name)} style={{ paddingVertical: theme.space(1.5) }}>
            <AppText size="sm">{t.name}</AppText>
          </Pressable>
        ))
      )}
    </View>
  );
}

function Row({ row, expanded, onToggle }: { row: AuditLogView; expanded: boolean; onToggle: () => void }) {
  const { theme } = useTheme();
  return (
    <Pressable onPress={onToggle}>
      <Card style={{ gap: theme.space(1) }}>
        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <AppText weight="700" numberOfLines={1} style={{ flexShrink: 1 }}>
            {row.action}
          </AppText>
          <Pill text={row.success ? "OK" : row.errorCode ?? "FAIL"} tone={row.success ? "success" : "danger"} />
        </View>
        <AppText size="xs" tone="muted">
          {(row.actorName ?? "system")}
          {row.actorRole ? ` · ${row.actorRole}` : ""} · {ago(row.at)}
        </AppText>
        <AppText size="xs" tone="faint" numberOfLines={1}>
          {row.httpMethod} {row.endpoint}
        </AppText>
        {expanded ? (
          <View style={{ gap: theme.space(1), marginTop: theme.space(1) }}>
            <Divider />
            <KV k="When" v={new Date(row.at).toLocaleString()} />
            <KV k="Actor" v={row.actorName ? `${row.actorName} (${row.actorPhoneMasked ?? "—"})` : "system"} />
            <KV k="Community" v={row.tenantName ?? "—"} />
            <KV k="Entity" v={row.entityType ? `${row.entityType} ${row.entityId ?? ""}`.trim() : "—"} />
            <KV k="Outcome" v={row.success ? "success" : `failed · ${row.errorCode ?? "?"}`} />
            <KV k="Duration" v={row.durationMs != null ? `${row.durationMs} ms` : "—"} />
            <KV k="Request id" v={row.requestId ?? "—"} />
            {row.detail ? <KV k="Detail" v={row.detail} /> : null}
          </View>
        ) : null}
      </Card>
    </Pressable>
  );
}

function KV({ k, v }: { k: string; v: string }) {
  const { theme } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: theme.space(2) }}>
      <AppText size="xs" tone="faint" style={{ width: 84 }}>
        {k}
      </AppText>
      <AppText size="xs" style={{ flex: 1 }}>
        {v}
      </AppText>
    </View>
  );
}
