import React, { useState } from "react";
import { Alert, View } from "react-native";
import { AppText, Card } from "@/components/Themed";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { useTheme } from "@/theme/ThemeProvider";

/**
 * Compose + confirm one announcement. The parent owns the target (scope / community) and the
 * API call; this component just collects the copy and guards the irreversible send.
 */
export function BroadcastComposer({
  targetLabel,
  disabled,
  onSend,
}: {
  targetLabel: string;
  disabled?: boolean;
  onSend: (title: string, body: string) => Promise<void>;
}) {
  const { theme } = useTheme();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [sentAt, setSentAt] = useState<string | null>(null);

  const valid = title.trim().length >= 3 && body.trim().length >= 3 && !disabled;

  function confirm() {
    setErr(null);
    Alert.alert("Send announcement?", `This messages ${targetLabel} right now.`, [
      { text: "Cancel", style: "cancel" },
      { text: "Send", style: "destructive", onPress: send },
    ]);
  }

  async function send() {
    setBusy(true);
    setErr(null);
    try {
      await onSend(title.trim(), body.trim());
      setTitle("");
      setBody("");
      setSentAt(new Date().toLocaleTimeString());
    } catch (e: any) {
      setErr(e?.message ?? "Could not send");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card style={{ gap: theme.space(2) }}>
      <AppText weight="700">New announcement</AppText>
      <AppText size="xs" tone="faint">
        Goes to {targetLabel}.
      </AppText>
      <Field label="Title" value={title} onChangeText={setTitle} placeholder="Short headline" />
      <Field
        label="Message"
        value={body}
        onChangeText={setBody}
        placeholder="What do people need to know?"
        multiline
        numberOfLines={4}
        style={{ minHeight: 96, textAlignVertical: "top" }}
      />
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}
      {sentAt ? (
        <AppText tone="success" size="sm">
          Sent at {sentAt}.
        </AppText>
      ) : null}
      <Button label="Send announcement" onPress={confirm} loading={busy} disabled={!valid} />
    </Card>
  );
}
