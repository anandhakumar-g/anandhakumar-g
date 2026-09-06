import React, { useState } from "react";
import { View } from "react-native";
import { Button } from "@/components/Button";
import { AppText, Card } from "@/components/Themed";
import { canDownloadCsv, downloadCsv } from "@/lib/download";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

/** MVP-12 (C): a web-only card of one-tap CSV downloads. Renders nothing on native. */
export function ExportCsvCard({
  title = "Export CSV",
  items,
}: {
  title?: string;
  items: { label: string; path: string }[];
}) {
  const { theme } = useTheme();
  const { token } = useSession();
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  if (!canDownloadCsv) return null;

  async function go(path: string) {
    setBusy(path);
    setErr(null);
    try {
      await downloadCsv(path, token);
    } catch (e: any) {
      setErr(e?.message ?? "Download failed");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card style={{ gap: theme.space(2) }}>
      <AppText size="sm" weight="700">
        {title}
      </AppText>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
        {items.map((it) => (
          <Button
            key={it.path}
            label={it.label}
            variant="secondary"
            fullWidth={false}
            loading={busy === it.path}
            onPress={() => go(it.path)}
          />
        ))}
      </View>
      {err ? (
        <AppText tone="danger" size="xs">
          {err}
        </AppText>
      ) : null}
    </Card>
  );
}
