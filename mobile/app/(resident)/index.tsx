import { useFocusEffect, useRouter } from "expo-router";
import React, { useCallback } from "react";
import { View } from "react-native";
import { catalog, onboarding, tickets } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/Bits";
import { DashboardSummary } from "@/components/DashboardSummary";
import { Icon } from "@/components/Icon";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { TicketRow } from "@/components/TicketRow";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function ResidentHome() {
  const { theme } = useTheme();
  const router = useRouter();
  const { me, user } = useSession();
  const cats = useAsync(() => catalog.categories(), []);
  const list = useAsync(() => tickets.list(undefined, 0), []);
  const communityReq = useAsync(() => onboarding.myRequest(), []);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  const catName = (id: string) => cats.data?.find((c) => c.id === id)?.name;
  const recent = list.data?.content.slice(0, 4) ?? [];
  const noCommunity = !me?.activeTenantId;
  const pendingCommunity = communityReq.data?.status === "PENDING_REVIEW" ? communityReq.data : null;

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <View style={{ gap: theme.space(1), marginTop: theme.space(2) }}>
        <AppText tone="muted">Hi {user?.name?.split(" ")[0] ?? "there"} 👋</AppText>
        <AppText size="xl" weight="700">
          {me?.activeTenantBranding?.name ?? "Single Point"}
        </AppText>
      </View>

      <DashboardSummary />

      {noCommunity && pendingCommunity ? (
        <Card style={{ gap: theme.space(2) }}>
          <AppText weight="700">"{pendingCommunity.name}" is under review</AppText>
          <AppText size="sm" tone="faint">
            We're checking your community request. You'll get a notification once it's approved —
            then you'll be its admin.
          </AppText>
          <Button label="Book a service" variant="secondary" fullWidth={false} onPress={() => router.push("/(resident)/raise")} />
        </Card>
      ) : noCommunity ? (
        <Card style={{ gap: theme.space(2) }}>
          <AppText weight="700">You're not in a community</AppText>
          <AppText size="sm" tone="faint">
            You can still book a verified service provider directly. Join your community with an
            invite code, or register a new community.
          </AppText>
          <View style={{ flexDirection: "row", gap: theme.space(2), flexWrap: "wrap" }}>
            <Button label="Book a service" fullWidth={false} onPress={() => router.push("/(resident)/raise")} />
            <Button
              label="Join a community"
              variant="secondary"
              fullWidth={false}
              onPress={() => router.push("/(resident)/community")}
            />
            <Button
              label="Register a community"
              variant="secondary"
              fullWidth={false}
              onPress={() => router.push("/(resident)/register-community")}
            />
          </View>
        </Card>
      ) : (
        <Button
          label="Raise a ticket"
          left={<Icon name="add" size="sm" color={theme.color.primaryText} />}
          onPress={() => router.push("/(resident)/raise")}
        />
      )}

      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: theme.space(2) }}>
        <AppText weight="700">Recent</AppText>
        {recent.length > 0 ? (
          <AppText tone="primary" size="sm" onPress={() => router.push("/(resident)/tickets")}>
            View all
          </AppText>
        ) : null}
      </View>

      {list.loading ? (
        <Loading />
      ) : recent.length === 0 ? (
        <Card>
          <EmptyState
            title="No tickets yet"
            body="Something not working, or a question for the community team? Raise your first ticket — it takes a few taps."
            action={<Button label="Raise a ticket" fullWidth={false} onPress={() => router.push("/(resident)/raise")} />}
          />
        </Card>
      ) : (
        recent.map((t) => (
          <TicketRow key={t.id} ticket={t} hrefBase="/(resident)/ticket" categoryName={catName(t.categoryId)} />
        ))
      )}
    </Screen>
  );
}
