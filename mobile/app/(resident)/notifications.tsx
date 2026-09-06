import { useFocusEffect } from "expo-router";
import React, { useCallback } from "react";
import { Pressable, View } from "react-native";
import { me as meApi } from "@/api/endpoints";
import { NotificationView } from "@/api/types";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/Bits";
import { AppText, Card, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

function ago(iso: string): string {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function Notifications() {
  const { theme } = useTheme();
  const { refreshMe } = useSession();
  const list = useAsync(() => meApi.notifications(), []);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  const rows: NotificationView[] = list.data?.content ?? [];
  const hasUnread = rows.some((n) => !n.readAt);

  async function open(n: NotificationView) {
    if (n.readAt) return;
    try {
      await meApi.markNotificationRead(n.id);
      await Promise.all([list.refresh(), refreshMe()]);
    } catch {
      /* best effort */
    }
  }

  async function markAll() {
    try {
      await meApi.markAllNotificationsRead();
      await Promise.all([list.refresh(), refreshMe()]);
    } catch {
      /* best effort */
    }
  }

  return (
    <Screen
      onRefresh={() => Promise.all([list.refresh(), refreshMe()])}
      refreshing={list.refreshing}
      loading={list.loading && rows.length === 0}
    >
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: theme.space(2) }}>
        <AppText size="xl" weight="700">
          Alerts
        </AppText>
        {hasUnread ? (
          <AppText tone="primary" size="sm" onPress={markAll}>
            Mark all read
          </AppText>
        ) : null}
      </View>

      {rows.length === 0 ? (
        <Card>
          <EmptyState title="Nothing yet" body="Ticket updates, offers and community announcements will show up here." />
        </Card>
      ) : (
        rows.map((n) => (
          <Pressable key={n.id} onPress={() => open(n)}>
            <Card style={{ gap: theme.space(1), opacity: n.readAt ? 0.7 : 1 }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                <View style={{ flexDirection: "row", alignItems: "center", gap: theme.space(2), flexShrink: 1 }}>
                  {n.readAt ? null : (
                    <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: theme.color.primary }} />
                  )}
                  <AppText weight={n.readAt ? "500" : "700"} numberOfLines={1} style={{ flexShrink: 1 }}>
                    {n.title ?? "Notification"}
                  </AppText>
                </View>
                <AppText size="xs" tone="faint">
                  {ago(n.createdAt)}
                </AppText>
              </View>
              {n.body ? (
                <AppText size="sm" tone="muted" numberOfLines={3}>
                  {n.body}
                </AppText>
              ) : null}
            </Card>
          </Pressable>
        ))
      )}
    </Screen>
  );
}
