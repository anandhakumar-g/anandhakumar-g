import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { providerProfile } from "@/api/endpoints";
import { Availability, ProviderProfile } from "@/api/types";
import { Segmented } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { SettingsScreen } from "@/components/SettingsScreen";
import { AppText, Card } from "@/components/Themed";
import { useTheme } from "@/theme/ThemeProvider";

const OPTIONS: { value: Availability; label: string }[] = [
  { value: "AVAILABLE", label: "Available" },
  { value: "BUSY", label: "Busy" },
  { value: "AWAY", label: "Away" },
];

export default function ProviderSettings() {
  const { theme } = useTheme();
  const [profile, setProfile] = useState<ProviderProfile | null>(null);
  const [note, setNote] = useState("");
  const [area, setArea] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    providerProfile.get().then((p) => {
      setProfile(p);
      setNote(p.availabilityNote ?? "");
      setArea(p.serviceArea ?? "");
    }).catch((e) => setErr(e.message ?? "Failed to load profile"));
  }, []);

  async function save(next: Partial<ProviderProfile>) {
    setBusy(true);
    setErr(null);
    try {
      const saved = await providerProfile.update({
        availability: next.availability,
        availabilityNote: note.trim() || undefined,
        serviceArea: area.trim() || undefined,
      });
      setProfile(saved);
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <SettingsScreen showBilling>
      {profile ? (
        <Card style={{ gap: theme.space(2) }}>
          <AppText weight="700">My listing</AppText>
          {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}
          <AppText size="xs" tone="faint">Availability is shown to community admins — it doesn't block work.</AppText>
          <Segmented
            value={profile.availability}
            onChange={(v) => save({ availability: v as Availability })}
            options={OPTIONS}
          />
          <Field placeholder="Availability note (e.g. Back Monday)" value={note} onChangeText={setNote} />
          <Field placeholder="Service area" value={area} onChangeText={setArea} />
          <Button label="Save" loading={busy} onPress={() => save({})} />
        </Card>
      ) : null}
    </SettingsScreen>
  );
}
