import React from "react";
import { offers as offersApi } from "@/api/endpoints";
import { EmptyState } from "@/components/Bits";
import { OfferCard } from "@/components/OfferCard";
import { AppText, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function Deals() {
  const { theme } = useTheme();
  const feed = useAsync(() => offersApi.feed(), []);

  return (
    <Screen onRefresh={feed.refresh} refreshing={feed.refreshing}>
      <AppText size="xxl" weight="700">
        Deals
      </AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Offers from verified vendors for your community.
      </AppText>

      {feed.loading ? (
        <Loading />
      ) : (feed.data?.length ?? 0) === 0 ? (
        <EmptyState
          title="No deals right now"
          body="When vendors publish offers for your community, they'll show up here. Manage which categories notify you in Settings."
        />
      ) : (
        feed.data!.map((o) => <OfferCard key={o.id} offer={o} hrefBase="/(resident)/offer" />)
      )}
    </Screen>
  );
}
