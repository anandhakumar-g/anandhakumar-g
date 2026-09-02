import React, { useState } from "react";
import { View } from "react-native";
import { taxonomy } from "@/api/endpoints";
import { AdminVendorCategory, VendorCategoryKind } from "@/api/types";
import { Divider, EmptyState, Pill } from "@/components/Bits";
import { Button } from "@/components/Button";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";

export default function Taxonomy() {
  const { theme } = useTheme();
  const kinds = useAsync(() => taxonomy.kinds(), []);
  const cats = useAsync(() => taxonomy.categories(), []);
  const [err, setErr] = useState<string | null>(null);
  const [newKindCode, setNewKindCode] = useState("");
  const [newKindLabel, setNewKindLabel] = useState("");
  const [newCatName, setNewCatName] = useState("");
  const [newCatKind, setNewCatKind] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function run(fn: () => Promise<any>) {
    setBusy(true);
    setErr(null);
    try {
      await fn();
      kinds.reload();
      cats.reload();
    } catch (e: any) {
      setErr(e.message ?? "Failed");
    } finally {
      setBusy(false);
    }
  }

  const activeKinds = (kinds.data ?? []).filter((k) => k.active);

  return (
    <Screen onRefresh={() => { kinds.refresh(); cats.refresh(); }} refreshing={kinds.refreshing}>
      <AppText size="xxl" weight="700">
        Vendor taxonomy
      </AppText>
      <AppText tone="muted" style={{ marginTop: -theme.space(1) }}>
        Open new verticals and categories platform-wide — no deploy needed.
      </AppText>
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">New vertical</AppText>
        <Field label="Code" value={newKindCode} onChangeText={setNewKindCode} autoCapitalize="characters" placeholder="PET_CARE" />
        <Field label="Label" value={newKindLabel} onChangeText={setNewKindLabel} placeholder="Pet Care" />
        <Button
          label="Add vertical"
          loading={busy}
          disabled={!newKindCode.trim() || !newKindLabel.trim()}
          onPress={() => run(async () => {
            await taxonomy.createKind(newKindCode.trim(), newKindLabel.trim());
            setNewKindCode(""); setNewKindLabel("");
          })}
        />
      </Card>

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">New category</AppText>
        <Field label="Name" value={newCatName} onChangeText={setNewCatName} placeholder="Dog Grooming" />
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
          {activeKinds.map((k) => (
            <Button
              key={k.id}
              label={k.label}
              fullWidth={false}
              variant={newCatKind === k.code ? "primary" : "secondary"}
              onPress={() => setNewCatKind(k.code)}
            />
          ))}
        </View>
        <Button
          label="Add category"
          loading={busy}
          disabled={!newCatName.trim() || !newCatKind}
          onPress={() => run(async () => {
            await taxonomy.createCategory(newCatName.trim(), newCatKind!);
            setNewCatName(""); setNewCatKind(null);
          })}
        />
      </Card>

      <Divider />
      <AppText weight="700">Verticals</AppText>
      {kinds.loading ? <Loading /> : activeKinds.length === 0 ? (
        <EmptyState title="No verticals" />
      ) : (
        (kinds.data ?? []).map((k: VendorCategoryKind) => (
          <Card key={k.id} style={{ gap: theme.space(1) }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
              <AppText weight="700">{k.label}</AppText>
              {k.active ? (
                <AppText tone="danger" size="sm" onPress={() => run(() => taxonomy.deactivateKind(k.id))}>
                  Deactivate
                </AppText>
              ) : <Pill text="inactive" />}
            </View>
            <AppText size="xs" tone="faint">{k.code}</AppText>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(1.5) }}>
              {(cats.data ?? []).filter((c) => c.kind === k.code).map((c: AdminVendorCategory) => (
                <View
                  key={c.id}
                  style={{
                    flexDirection: "row", alignItems: "center", gap: theme.space(1.5),
                    backgroundColor: theme.color.surfaceAlt, borderRadius: theme.radius.pill,
                    paddingHorizontal: theme.space(2.5), paddingVertical: theme.space(1),
                    opacity: c.active ? 1 : 0.5,
                  }}
                >
                  <AppText size="xs">{c.name}</AppText>
                  <AppText
                    size="xs"
                    tone={c.active ? "danger" : "primary"}
                    onPress={() => run(() =>
                      c.active ? taxonomy.deactivateCategory(c.id) : taxonomy.reactivateCategory(c.id))}
                  >
                    {c.active ? "✕" : "↺"}
                  </AppText>
                </View>
              ))}
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
