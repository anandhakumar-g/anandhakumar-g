import React, { useState } from "react";
import { View } from "react-native";
import { offers as offersApi } from "@/api/endpoints";
import { discountLabel } from "@/api/types";
import { EmptyState, Pill } from "@/components/Bits";
import { OfferForm } from "@/components/OfferForm";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function AdminOffers() {
  const { theme } = useTheme();
  const { me } = useSession();
  const list = useAsync(() => offersApi.mine(), []);
  const [creating, setCreating] = useState(false);

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="xxl" weight="700">
          Community offers
        </AppText>
        <AppText tone="primary" size="sm" onPress={() => setCreating((v) => !v)}>
          {creating ? "Close" : "＋ New"}
        </AppText>
      </View>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Run a community-wide promotion. Super Admin reviews every offer before it publishes.
      </AppText>

      {creating ? (
        <OfferForm
          tenantId={me?.activeTenantId}
          onSaved={() => {
            setCreating(false);
            list.reload();
          }}
        />
      ) : null}

      {list.loading ? (
        <Loading />
      ) : (list.data?.length ?? 0) === 0 ? (
        <EmptyState title="No offers yet" body="Create a promotion for your residents — a festival discount, a vendor tie-up, a clubhouse deal." />
      ) : (
        list.data!.map((o) => (
          <Card key={o.id} style={{ gap: theme.space(1.5) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700" numberOfLines={1} style={{ flexShrink: 1 }}>
                {o.title}
              </AppText>
              <Pill
                text={o.status.replace(/_/g, " ")}
                tone={o.status === "ACTIVE" ? "success" : o.status === "REJECTED" ? "danger" : "muted"}
              />
            </View>
            <AppText size="sm" tone="muted">
              {discountLabel(o)} · {o.target?.summary ?? "audience pending"}
            </AppText>
            {o.status === "REJECTED" && o.rejectReason ? (
              <AppText size="xs" tone="danger">
                Rejected: {o.rejectReason}
              </AppText>
            ) : null}
          </Card>
        ))
      )}
    </Screen>
  );
}
