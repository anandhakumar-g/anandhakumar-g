import { Stack } from "expo-router";
import React from "react";
import { View } from "react-native";
import { broadcasts as api } from "@/api/endpoints";
import { BroadcastComposer } from "@/components/BroadcastComposer";
import { Divider, EmptyState, Pill } from "@/components/Bits";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function AdminBroadcast() {
  const { theme } = useTheme();
  const { me } = useSession();
  const history = useAsync(() => api.listAdmin(0), []);
  const community = me?.activeTenantBranding?.name ?? "your community";

  return (
    <Screen onRefresh={history.refresh} refreshing={history.refreshing}>
      <Stack.Screen options={{ headerShown: true, title: "Announce" }} />
      <AppText size="xl" weight="700">
        Announce
      </AppText>

      <BroadcastComposer
        targetLabel={`every resident of ${community}`}
        onSend={async (title, body) => {
          await api.sendAdmin(title, body);
          history.reload();
        }}
      />

      <Divider />
      <AppText weight="700">Recent announcements</AppText>
      {history.loading ? (
        <Loading />
      ) : (history.data?.content?.length ?? 0) === 0 ? (
        <EmptyState title="None yet" body="Your community announcements will show here." />
      ) : (
        history.data!.content.map((b) => (
          <Card key={b.id} style={{ gap: theme.space(1) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700" numberOfLines={1} style={{ flexShrink: 1 }}>
                {b.title}
              </AppText>
              <Pill text={`${b.recipientCount}`} tone="muted" />
            </View>
            <AppText size="sm" tone="muted">
              {b.body}
            </AppText>
            <AppText size="xs" tone="faint">
              {new Date(b.at).toLocaleString()}
            </AppText>
          </Card>
        ))
      )}
    </Screen>
  );
}
