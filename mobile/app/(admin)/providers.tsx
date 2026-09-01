import { useFocusEffect } from "expo-router";
import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { admin, catalog } from "@/api/endpoints";
import { ProviderView, VendorCategory } from "@/api/types";
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
        <AppText size="xl" weight="700">
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
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
            {(vcats.data ?? []).map((v: VendorCategory) => (
              <Button
                key={v.id}
                label={v.name}
                variant={vcat === v.id ? "primary" : "secondary"}
                fullWidth={false}
                onPress={() => setVcat(v.id)}
              />
            ))}
          </View>
          <Button label="Add provider (pending verification)" onPress={create} disabled={!name.trim() || !phone.trim() || !vcat} />
        </Card>
      ) : null}

      <Divider />
      {list.loading ? (
        <Loading />
      ) : (list.data?.length ?? 0) === 0 ? (
        <EmptyState title="No providers yet" body="Add your electrician, plumber, housekeeping vendor, and verify them before assigning tickets." />
      ) : (
        list.data!.map((p) => (
          <Card key={p.id} style={{ gap: theme.space(2) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700">{p.name}</AppText>
              <Pill
                text={p.verificationStatus.replace(/_/g, " ")}
                tone={p.verificationStatus === "VERIFIED" ? "success" : p.verificationStatus === "REJECTED" ? "danger" : "muted"}
              />
            </View>
            <AppText size="xs" tone="faint">
              {p.contactPhoneMasked} · {p.assignable ? "assignable" : "not assignable"}
            </AppText>
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
