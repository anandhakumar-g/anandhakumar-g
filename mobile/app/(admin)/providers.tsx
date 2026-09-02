import { useFocusEffect } from "expo-router";
import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { admin, catalog, kyc as kycApi } from "@/api/endpoints";
import { groupVendorCategories, KycDocView, ProviderView } from "@/api/types";
import { Button } from "@/components/Button";
import { Divider, EmptyState, Pill } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function Providers() {
  const { theme } = useTheme();
  const list = useAsync(() => admin.providers(), []);
  const vcats = useAsync(() => catalog.vendorCategories(), []);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [vcat, setVcat] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [openKyc, setOpenKyc] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      list.refresh();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
  );

  async function create() {
    if (!vcat) return;
    setErr(null);
    try {
      await admin.addProvider({ name: name.trim(), vendorCategoryId: vcat, company: true, contactPhone: phone.trim() });
      setName("");
      setPhone("");
      setVcat(null);
      setAdding(false);
      list.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    }
  }

  async function setStatus(p: ProviderView, status: string) {
    setBusyId(p.id);
    setErr(null);
    try {
      await admin.verifyProvider(p.id, status);
      list.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Screen onRefresh={list.refresh} refreshing={list.refreshing}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="xxl" weight="700">
          Providers
        </AppText>
        <AppText tone="primary" size="sm" onPress={() => setAdding((v) => !v)}>
          {adding ? "Close" : "＋ Add"}
        </AppText>
      </View>
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      {adding ? (
        <Card style={{ gap: theme.space(2) }}>
          <Field label="Name / company" value={name} onChangeText={setName} />
          <Field label="Contact phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <AppText size="sm" weight="600" tone="muted">
            Category
          </AppText>
          {groupVendorCategories(vcats.data ?? []).map((group) => (
            <View key={group.kind} style={{ gap: theme.space(1.5) }}>
              <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
                {group.kindLabel.toUpperCase()}
              </AppText>
              <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
                {group.items.map((v) => (
                  <Button
                    key={v.id}
                    label={v.name}
                    variant={vcat === v.id ? "primary" : "secondary"}
                    fullWidth={false}
                    onPress={() => setVcat(v.id)}
                  />
                ))}
              </View>
            </View>
          ))}
          <Button label="Add provider (pending verification)" onPress={create} disabled={!name.trim() || !phone.trim() || !vcat} />
        </Card>
      ) : null}

      <Divider />
      {list.loading ? (
        <Loading />
      ) : (list.data?.length ?? 0) === 0 ? (
        <EmptyState title="No providers yet" body="Add a vendor, review their KYC documents, then verify them before assigning work." />
      ) : (
        list.data!.map((p) => (
          <Card key={p.id} style={{ gap: theme.space(2) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
              <AppText weight="700" style={{ flexShrink: 1 }}>{p.name}</AppText>
              <View style={{ flexDirection: "row", gap: theme.space(1.5), alignItems: "center" }}>
                {p.tier === "FEATURED" ? <Pill text="★ Featured" tone="primary" /> : null}
                <Pill
                  text={p.verificationStatus.replace(/_/g, " ")}
                  tone={p.verificationStatus === "VERIFIED" ? "success" : p.verificationStatus === "REJECTED" ? "danger" : "muted"}
                />
              </View>
            </View>
            <AppText size="xs" tone="faint">
              {p.contactPhoneMasked} · {p.assignable ? "assignable" : "not assignable"}
            </AppText>

            <AppText
              tone="primary"
              size="sm"
              onPress={() => setOpenKyc((cur) => (cur === p.id ? null : p.id))}
            >
              {openKyc === p.id ? "Hide KYC documents" : "Review KYC documents"}
            </AppText>
            {openKyc === p.id ? <KycPanel providerId={p.id} /> : null}

            <View style={{ flexDirection: "row", gap: theme.space(2), flexWrap: "wrap" }}>
              {p.verificationStatus !== "VERIFIED" ? (
                <Button label="Verify" fullWidth={false} loading={busyId === p.id} onPress={() => setStatus(p, "VERIFIED")} />
              ) : (
                <Button label="Suspend" variant="secondary" fullWidth={false} loading={busyId === p.id} onPress={() => setStatus(p, "SUSPENDED")} />
              )}
              {p.verificationStatus !== "REJECTED" ? (
                <Button label="Reject" variant="danger" fullWidth={false} loading={busyId === p.id} onPress={() => setStatus(p, "REJECTED")} />
              ) : null}
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}

function KycPanel({ providerId }: { providerId: string }) {
  const { theme } = useTheme();
  const q = useAsync(() => kycApi.forProvider(providerId), [providerId]);
  const [busy, setBusy] = useState<string | null>(null);

  async function review(doc: KycDocView, status: "ACCEPTED" | "REJECTED") {
    setBusy(doc.id + status);
    try {
      await kycApi.review(providerId, doc.id, status);
      q.reload();
    } finally {
      setBusy(null);
    }
  }

  if (q.loading) return <Loading />;
  if ((q.data?.length ?? 0) === 0) {
    return (
      <AppText size="xs" tone="faint">
        No documents uploaded yet.
      </AppText>
    );
  }

  return (
    <View style={{ gap: theme.space(2), backgroundColor: theme.color.surfaceAlt, borderRadius: theme.radius.sm, padding: theme.space(3) }}>
      {q.data!.map((d) => (
        <View key={d.id} style={{ gap: theme.space(1.5) }}>
          <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
            <AppText size="sm" weight="600">
              {d.docType.replace(/_/g, " ")}
            </AppText>
            <Pill
              text={d.status}
              tone={d.status === "ACCEPTED" ? "success" : d.status === "REJECTED" ? "danger" : "muted"}
            />
          </View>
          <AppText size="xs" tone="faint">
            {d.originalFilename ?? "document"} · {(d.sizeBytes / 1024).toFixed(0)} KB
          </AppText>
          {d.status === "PENDING" ? (
            <View style={{ flexDirection: "row", gap: theme.space(2) }}>
              <Button label="Accept" fullWidth={false} loading={busy === d.id + "ACCEPTED"} onPress={() => review(d, "ACCEPTED")} />
              <Button label="Reject" variant="danger" fullWidth={false} loading={busy === d.id + "REJECTED"} onPress={() => review(d, "REJECTED")} />
            </View>
          ) : null}
        </View>
      ))}
    </View>
  );
}
