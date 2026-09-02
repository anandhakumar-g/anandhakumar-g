import * as ImagePicker from "expo-image-picker";
import React, { useState } from "react";
import { View } from "react-native";
import { kyc as kycApi } from "@/api/endpoints";
import { KycDocView } from "@/api/types";
import { Pill } from "@/components/Bits";
import { Button } from "@/components/Button";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

const SLOTS: { type: string; label: string; hint: string }[] = [
  { type: "GOV_ID", label: "Government ID", hint: "Aadhaar, PAN, passport or driving licence" },
  { type: "ADDRESS_PROOF", label: "Address proof", hint: "Utility bill, rent agreement or bank statement" },
  { type: "COMPANY_REG", label: "Company registration", hint: "GST / incorporation certificate (companies only)" },
];

export default function ProviderKyc() {
  const { theme } = useTheme();
  const docs = useAsync(() => kycApi.mine(), []);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function pickAndUpload(docType: string) {
    setErr(null);
    const res = await ImagePicker.launchImageLibraryAsync({ quality: 0.7, mediaTypes: ImagePicker.MediaTypeOptions.Images });
    if (res.canceled) return;
    const asset = res.assets[0];
    setBusy(docType);
    try {
      await kycApi.upload(docType, {
        uri: asset.uri,
        name: asset.fileName ?? `${docType.toLowerCase()}.jpg`,
        type: asset.mimeType ?? "image/jpeg",
      });
      docs.reload();
    } catch (e: any) {
      setErr(e.message ?? "Upload failed");
    } finally {
      setBusy(null);
    }
  }

  const latest = (type: string): KycDocView | undefined =>
    (docs.data ?? []).filter((d) => d.docType === type).slice(-1)[0];

  return (
    <Screen onRefresh={docs.refresh} refreshing={docs.refreshing}>
      <AppText size="xxl" weight="700">
        Verification
      </AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Upload your documents. An admin reviews them before you can be assigned work.
      </AppText>
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      {docs.loading ? (
        <Loading />
      ) : (
        SLOTS.map((slot) => {
          const doc = latest(slot.type);
          return (
            <Card key={slot.type} style={{ gap: theme.space(2) }}>
              <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                <AppText weight="700">{slot.label}</AppText>
                {doc ? (
                  <Pill
                    text={doc.status}
                    tone={doc.status === "ACCEPTED" ? "success" : doc.status === "REJECTED" ? "danger" : "muted"}
                  />
                ) : (
                  <Pill text="Not uploaded" />
                )}
              </View>
              <AppText size="xs" tone="faint">
                {slot.hint}
              </AppText>
              {doc?.reviewNote ? (
                <AppText size="xs" tone={doc.status === "REJECTED" ? "danger" : "muted"}>
                  Reviewer: {doc.reviewNote}
                </AppText>
              ) : null}
              <Button
                label={doc ? "Replace" : "Upload"}
                variant="secondary"
                loading={busy === slot.type}
                onPress={() => pickAndUpload(slot.type)}
              />
            </Card>
          );
        })
      )}
    </Screen>
  );
}
