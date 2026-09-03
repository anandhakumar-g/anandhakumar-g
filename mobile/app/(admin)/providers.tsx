import { useFocusEffect } from "expo-router";
import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { admin } from "@/api/endpoints";
import { ProviderView } from "@/api/types";
import { ratingText } from "@/lib/format";
import { Button } from "@/components/Button";
import { Divider, EmptyState, Pill, Segmented } from "@/components/Bits";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

type Tab = "enrolled" | "browse";

export default function Providers() {
  const { theme } = useTheme();
  const [tab, setTab] = useState<Tab>("enrolled");
  const list = useAsync(() => admin.providers({ includeInactive: true }), []);
  const settings = useAsync(() => admin.communitySettings(), []);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const canEnrol = settings.data?.providerOnboardingAllowed ?? false;
  const catalog = useAsync(
    () => (canEnrol ? admin.providerCatalog() : Promise.resolve([] as ProviderView[])),
    [canEnrol]
  );

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      settings.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  async function act(id: string, fn: () => Promise<any>) {
    setBusyId(id);
    setErr(null);
    try {
      await fn();
      list.reload();
      catalog.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusyId(null);
    }
  }

  const enrolledIds = new Set((list.data ?? []).map((p) => p.id));

  return (
    <Screen onRefresh={() => { list.refresh(); catalog.refresh(); }} refreshing={list.refreshing}>
      <AppText size="xxl" weight="700">Providers</AppText>
      <Segmented
        value={tab}
        onChange={setTab}
        options={[
          { value: "enrolled", label: "Enrolled" },
          { value: "browse", label: "Browse verified" },
        ]}
      />
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}

      {!canEnrol ? (
        <Card>
          <AppText size="xs" tone="faint">
            The platform hasn't enabled provider enrolment for this community yet. Ask your Super Admin
            to turn it on.
          </AppText>
        </Card>
      ) : null}

      {tab === "enrolled" &&
        (list.loading ? (
          <Loading />
        ) : (list.data?.length ?? 0) === 0 ? (
          <EmptyState title="No providers enrolled" body="Browse the verified directory to enrol one." />
        ) : (
          list.data!.map((p) => (
            <Card key={p.id} style={{ gap: theme.space(2) }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
                <AppText weight="700" style={{ flexShrink: 1 }}>{p.name}</AppText>
                <View style={{ flexDirection: "row", gap: theme.space(1.5), alignItems: "center" }}>
                  {p.tier === "FEATURED" ? <Pill text="★ Featured" tone="primary" /> : null}
                  {ratingText(p.ratingAvg, p.ratingCount) ? <Pill text={ratingText(p.ratingAvg, p.ratingCount)!} /> : null}
                  <Pill
                    text={p.verificationStatus.replace(/_/g, " ")}
                    tone={p.verificationStatus === "VERIFIED" ? "success" : "muted"}
                  />
                </View>
              </View>
              <AppText size="xs" tone="faint">
                {p.contactPhoneMasked} · {p.active ? (p.assignable ? "assignable" : "not assignable") : "removed from directory"}
                {p.availability !== "AVAILABLE" ? ` · ${p.availability.toLowerCase()}` : ""}
              </AppText>
              {p.active ? (
                <Button label="Remove from directory" variant="secondary" fullWidth={false}
                  loading={busyId === p.id} onPress={() => act(p.id, () => admin.deactivateProvider(p.id))} />
              ) : (
                <Button label="Restore" fullWidth={false}
                  loading={busyId === p.id} onPress={() => act(p.id, () => admin.reactivateProvider(p.id))} />
              )}
            </Card>
          ))
        ))}

      {tab === "browse" && (
        <>
          <Divider />
          {!canEnrol ? null : catalog.loading ? (
            <Loading />
          ) : (catalog.data?.length ?? 0) === 0 ? (
            <EmptyState title="No verified providers" body="The Super Admin adds and verifies providers globally." />
          ) : (
            catalog.data!.map((p) => (
              <Card key={p.id} style={{ gap: theme.space(1.5) }}>
                <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
                  <AppText weight="700" style={{ flexShrink: 1 }}>{p.name}</AppText>
                  {ratingText(p.ratingAvg, p.ratingCount) ? <Pill text={ratingText(p.ratingAvg, p.ratingCount)!} /> : null}
                </View>
                <AppText size="xs" tone="faint">{p.contactPhoneMasked}</AppText>
                {enrolledIds.has(p.id) ? (
                  <Pill text="Enrolled" tone="success" />
                ) : (
                  <Button label="Enrol in this community" fullWidth={false}
                    loading={busyId === p.id} onPress={() => act(p.id, () => admin.enrolProvider(p.id))} />
                )}
              </Card>
            ))
          )}
        </>
      )}
    </Screen>
  );
}
