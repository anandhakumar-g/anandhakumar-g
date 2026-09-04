import { Stack } from "expo-router";
import React, { useState } from "react";
import { Pressable, View } from "react-native";
import { broadcasts as api, communities } from "@/api/endpoints";
import type { BroadcastScope } from "@/api/types";
import { BroadcastComposer } from "@/components/BroadcastComposer";
import { Divider, EmptyState, Pill, Segmented } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function SuperBroadcast() {
  const { theme } = useTheme();
  const [scope, setScope] = useState<BroadcastScope>("ALL_ADMINS");
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const history = useAsync(() => api.listSuper(undefined, 0), []);

  const needsCommunity = scope === "COMMUNITY";
  const target =
    scope === "ALL_ADMINS"
      ? "every community admin"
      : scope === "ALL_USERS"
      ? "everyone on the platform"
      : tenantName
      ? `every resident of ${tenantName}`
      : "a community (pick one below)";

  return (
    <Screen onRefresh={history.refresh} refreshing={history.refreshing}>
      <Stack.Screen options={{ headerShown: true, title: "Announcement" }} />
      <AppText size="xl" weight="700">
        Send an announcement
      </AppText>

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
          AUDIENCE
        </AppText>
        <Segmented
          value={scope}
          onChange={(v) => {
            setScope(v);
            setTenantId(null);
            setTenantName(null);
          }}
          options={[
            { value: "ALL_ADMINS", label: "Admins" },
            { value: "ALL_USERS", label: "All users" },
            { value: "COMMUNITY", label: "A community" },
          ]}
        />
        {needsCommunity ? (
          <CommunityPicker
            selected={tenantName}
            onPick={(id, name) => {
              setTenantId(id);
              setTenantName(name);
            }}
          />
        ) : null}
      </Card>

      <BroadcastComposer
        targetLabel={target}
        disabled={needsCommunity && !tenantId}
        onSend={async (title, body) => {
          await api.sendSuper(scope, title, body, tenantId ?? undefined);
          history.reload();
        }}
      />

      <Divider />
      <AppText weight="700">Recent announcements</AppText>
      {history.loading ? (
        <Loading />
      ) : (history.data?.content?.length ?? 0) === 0 ? (
        <EmptyState title="None yet" body="Platform and community announcements will show here." />
      ) : (
        history.data!.content.map((b) => (
          <Card key={b.id} style={{ gap: theme.space(1) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700" numberOfLines={1} style={{ flexShrink: 1 }}>
                {b.title}
              </AppText>
              <Pill
                text={b.scope === "COMMUNITY" ? b.tenantName ?? "community" : b.scope === "ALL_ADMINS" ? "admins" : "all"}
                tone="primary"
              />
            </View>
            <AppText size="sm" tone="muted">
              {b.body}
            </AppText>
            <AppText size="xs" tone="faint">
              {new Date(b.at).toLocaleString()} · {b.recipientCount} recipients
            </AppText>
          </Card>
        ))
      )}
    </Screen>
  );
}

function CommunityPicker({
  selected,
  onPick,
}: {
  selected: string | null;
  onPick: (id: string, name: string) => void;
}) {
  const { theme } = useTheme();
  const [q, setQ] = useState("");
  const results = useAsync(() => communities.search(q.trim() || undefined), [q]);
  return (
    <View style={{ gap: theme.space(1) }}>
      <Field placeholder="Search communities" value={q} onChangeText={setQ} autoCapitalize="none" />
      {selected ? (
        <AppText size="xs" tone="success">
          Selected: {selected}
        </AppText>
      ) : null}
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
