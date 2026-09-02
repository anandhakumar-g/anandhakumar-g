import React, { useState } from "react";
import { me as meApi } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { SettingsScreen } from "@/components/SettingsScreen";
import { AppText, Card } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

export default function ResidentSettings() {
  const { theme } = useTheme();
  const { me, refreshMe } = useSession();
  const [date, setDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function save(value: string | null) {
    setBusy(true);
    setErr(null);
    try {
      await meApi.setAwayUntil(value);
      setDate("");
      await refreshMe();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  const awayUntil = me?.awayUntil ? new Date(me.awayUntil) : null;

  return (
    <SettingsScreen showOfferPrefs>
      <Card style={{ gap: theme.space(2) }}>
        <AppText weight="700">I'm away</AppText>
        <AppText size="xs" tone="faint">
          {awayUntil && awayUntil.getTime() > Date.now()
            ? `Your requests show "away until ${awayUntil.toLocaleDateString()}".`
            : "Let your community team know you may be slow to respond."}
        </AppText>
        {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}
        <Field placeholder="Away until (YYYY-MM-DD)" value={date} onChangeText={setDate} />
        <Button
          label="Set"
          loading={busy}
          disabled={!/^\d{4}-\d{2}-\d{2}$/.test(date.trim())}
          onPress={() => save(new Date(date.trim() + "T00:00:00Z").toISOString())}
        />
        {awayUntil ? (
          <Button label="Clear" variant="secondary" loading={busy} onPress={() => save(null)} />
        ) : null}
      </Card>
    </SettingsScreen>
  );
}
