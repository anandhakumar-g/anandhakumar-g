import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { catalog, superOffers } from "@/api/endpoints";
import { discountLabel, OfferView } from "@/api/types";
import { EmptyState, Pill, Segmented } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function SuperOffers() {
  const { theme } = useTheme();
  const queue = useAsync(() => superOffers.list("PENDING_APPROVAL"), []);
  const [enquiryCats, setEnquiryCats] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    catalog
      .categories()
      .then((cs) => setEnquiryCats(cs.filter((c) => c.requestType === "ENQUIRY").map((c) => ({ id: c.id, name: c.name }))))
      .catch(() => {});
  }, []);

  return (
    <Screen onRefresh={queue.refresh} refreshing={queue.refreshing}>
      <AppText size="xxl" weight="700">
        Offer approvals
      </AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Every offer is validated here before it reaches residents. Confirm or reshape the audience.
      </AppText>

      {queue.loading ? (
        <Loading />
      ) : (queue.data?.length ?? 0) === 0 ? (
        <EmptyState title="Nothing to review" body="Submitted offers from vendors and admins land here." />
      ) : (
        queue.data!.map((o) => (
          <OfferReview key={o.id} offer={o} enquiryCats={enquiryCats} onDone={queue.reload} />
        ))
      )}
    </Screen>
  );
}

function OfferReview({
  offer,
  enquiryCats,
  onDone,
}: {
  offer: OfferView;
  enquiryCats: { id: string; name: string }[];
  onDone: () => void;
}) {
  const { theme } = useTheme();
  const [mode, setMode] = useState<"KEEP" | "ALL" | "ENQUIRY" | "PEOPLE">("KEEP");
  const [enquiryCategoryId, setEnquiryCategoryId] = useState<string | null>(null);
  const [phonesText, setPhonesText] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<"" | "approve" | "reject">("");
  const [err, setErr] = useState<string | null>(null);

  const phones = phonesText.split(/[\s,]+/).map((s) => s.trim()).filter(Boolean);

  async function approve() {
    setErr(null);
    setBusy("approve");
    try {
      const override =
        mode === "ALL"
          ? { targetType: "ALL_TENANTS" }
          : mode === "ENQUIRY"
          ? { targetType: "ENQUIRY_BASED", enquiryCategoryId }
          : mode === "PEOPLE"
          ? { targetType: "USER_LIST", phones }
          : undefined;
      await superOffers.approve(offer.id, override);
      onDone();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy("");
    }
  }

  async function reject() {
    if (reason.trim().length < 3) return;
    setBusy("reject");
    try {
      await superOffers.reject(offer.id, reason.trim());
      onDone();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy("");
    }
  }

  return (
    <Card style={{ gap: theme.space(2.5) }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText weight="700" numberOfLines={1} style={{ flexShrink: 1 }}>
          {offer.title}
        </AppText>
        <Pill text={offer.createdByRole} />
      </View>
      <AppText size="sm" tone="muted">
        {discountLabel(offer)} · {offer.couponCode ?? "no code"} · till {new Date(offer.validTo).toLocaleDateString()}
      </AppText>
      {offer.description ? (
        <AppText size="sm" tone="muted">
          {offer.description}
        </AppText>
      ) : null}
      <AppText size="xs" tone="faint">
        Suggested: {offer.target?.summary ?? "—"}
      </AppText>

      <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
        AUDIENCE
      </AppText>
      <Segmented
        value={mode}
        onChange={(v) => setMode(v as any)}
        options={[
          { value: "KEEP", label: "Keep suggested" },
          { value: "ALL", label: "All" },
          { value: "ENQUIRY", label: "Enquired" },
          { value: "PEOPLE", label: "Specific people" },
        ]}
      />
      {mode === "ENQUIRY" ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
          {enquiryCats.map((c) => (
            <Button
              key={c.id}
              label={c.name}
              fullWidth={false}
              variant={enquiryCategoryId === c.id ? "primary" : "secondary"}
              onPress={() => setEnquiryCategoryId(c.id)}
            />
          ))}
        </View>
      ) : null}
      {mode === "PEOPLE" ? (
        <Field
          placeholder="Phone numbers, comma or space separated"
          value={phonesText}
          onChangeText={setPhonesText}
          multiline
          autoCapitalize="none"
        />
      ) : null}

      <Button
        label="Approve & publish"
        loading={busy === "approve"}
        disabled={(mode === "ENQUIRY" && !enquiryCategoryId) || (mode === "PEOPLE" && phones.length === 0)}
        onPress={approve}
      />

      <Field label="Reject reason" value={reason} onChangeText={setReason} placeholder="Why this can't run" />
      <Button label="Reject" variant="danger" loading={busy === "reject"} disabled={reason.trim().length < 3} onPress={reject} />

      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}
    </Card>
  );
}
