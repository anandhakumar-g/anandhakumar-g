import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import React, { useEffect, useState } from "react";
import { Platform, Pressable, Switch, View } from "react-native";
import { catalog, me as meApi } from "@/api/endpoints";
import { groupVendorCategories, NotificationPreferences, VendorCategory } from "@/api/types";
import { BillingCard } from "@/components/BillingCard";
import { Button } from "@/components/Button";
import { Divider, KeyValue, Segmented } from "@/components/Bits";
import { AppText, Card, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";
import { THEMES, THEME_LABELS, ThemeName } from "@/theme/tokens";

export function SettingsScreen({
  showOfferPrefs = false,
  showBilling = false,
}: {
  showOfferPrefs?: boolean;
  showBilling?: boolean;
}) {
  const { theme, themeName, isExplicit, setTheme } = useTheme();
  const { me, user, signOut, refreshMe } = useSession();
  const [pushMsg, setPushMsg] = useState<string | null>(null);

  async function choose(name: ThemeName | null) {
    setTheme(name);
    try {
      if (name) await meApi.setTheme(name);
    } catch {
      /* best effort */
    }
  }

  async function enablePush() {
    try {
      if (Platform.OS === "web") return setPushMsg("Push is only available in the mobile app.");
      if (!Device.isDevice) return setPushMsg("Push needs a physical device.");
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== "granted") return setPushMsg("Notification permission denied.");
      const token = (await Notifications.getExpoPushTokenAsync()).data;
      await meApi.registerDevice(token, Platform.OS === "ios" ? "ios" : "android", "expo");
      setPushMsg("Push notifications enabled.");
    } catch (e: any) {
      setPushMsg("Could not enable push: " + (e?.message ?? "error"));
    }
  }

  return (
    <Screen onRefresh={refreshMe}>
      <AppText size="xxl" weight="700">
        Settings
      </AppText>

      <Card style={{ gap: theme.space(1) }}>
        <AppText size="sm" weight="700" tone="muted">
          Account
        </AppText>
        <KeyValue k="Name" v={user?.name} />
        <KeyValue k="Phone" v={user?.phoneMasked} />
        <KeyValue k="Email" v={me?.email} />
        <KeyValue k="Role" v={user?.role} />
        <KeyValue k="Community" v={me?.activeTenantBranding?.name} />
      </Card>

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">
          Appearance
        </AppText>
        {(Object.keys(THEMES) as ThemeName[]).map((name) => {
          const active = isExplicit && themeName === name;
          const tk = THEMES[name];
          return (
            <Pressable
              key={name}
              onPress={() => choose(name)}
              style={{
                flexDirection: "row",
                alignItems: "center",
                gap: theme.space(3),
                padding: theme.space(3),
                borderRadius: theme.radius.sm,
                borderWidth: active ? 2 : 1,
                borderColor: active ? theme.color.primary : theme.color.border,
              }}
            >
              <View style={{ flexDirection: "row" }}>
                {[tk.color.bg, tk.color.primary, tk.color.accent].map((c, i) => (
                  <View
                    key={i}
                    style={{
                      width: 18, height: 18, borderRadius: 9, backgroundColor: c,
                      marginLeft: i ? -6 : 0, borderWidth: 1, borderColor: theme.color.border,
                    }}
                  />
                ))}
              </View>
              <AppText weight={active ? "700" : "500"}>{THEME_LABELS[name]}</AppText>
            </Pressable>
          );
        })}
        <Button label="Use community default" variant="ghost" onPress={() => choose(null)} />
      </Card>

      {showBilling ? <BillingCard /> : null}

      {showOfferPrefs ? <OfferNotificationPrefs /> : null}

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">
          Push
        </AppText>
        <Button label="Enable push notifications" variant="secondary" onPress={enablePush} />
        {pushMsg ? (
          <AppText size="xs" tone="muted">
            {pushMsg}
          </AppText>
        ) : null}
      </Card>

      <Divider />
      <Button label="Sign out" variant="danger" onPress={signOut} />
    </Screen>
  );
}

function OfferNotificationPrefs() {
  const { theme } = useTheme();
  const [prefs, setPrefs] = useState<NotificationPreferences | null>(null);
  const [cats, setCats] = useState<VendorCategory[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    meApi.notificationPreferences().then(setPrefs).catch(() => {});
    catalog.vendorCategories().then(setCats).catch(() => {});
  }, []);

  async function patch(next: Partial<NotificationPreferences>) {
    if (!prefs) return;
    const optimistic = { ...prefs, ...next };
    setPrefs(optimistic);
    setSaving(true);
    try {
      const saved = await meApi.updateNotificationPreferences(next);
      setPrefs(saved);
    } catch {
      setPrefs(prefs);
    } finally {
      setSaving(false);
    }
  }

  if (!prefs) return null;
  const subscribed = new Set(prefs.subscribedVendorCategoryIds);

  function toggleCat(id: string) {
    const nextSet = new Set(subscribed);
    nextSet.has(id) ? nextSet.delete(id) : nextSet.add(id);
    patch({ subscribedVendorCategoryIds: [...nextSet] });
  }

  return (
    <Card style={{ gap: theme.space(3) }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="sm" weight="700" tone="muted">
          Offer notifications
        </AppText>
        {saving ? <AppText size="xs" tone="faint">saving…</AppText> : null}
      </View>

      <Row
        label="Ticket updates"
        hint="Status changes on your requests"
        value={prefs.ticketNotificationsEnabled}
        onValueChange={(v) => patch({ ticketNotificationsEnabled: v })}
      />
      <Row
        label="Promotional offers"
        hint="Deals from vendors you follow"
        value={prefs.promoNotificationsEnabled}
        onValueChange={(v) => patch({ promoNotificationsEnabled: v })}
      />

      <View style={{ gap: theme.space(1) }}>
        <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
          NOTIFY ME ABOUT
        </AppText>
        <AppText size="xs" tone="faint">
          You start subscribed to nothing — pick the categories you care about.
        </AppText>
      </View>
      {groupVendorCategories(cats).map((g) => (
        <View key={g.kind} style={{ gap: theme.space(1.5) }}>
          <AppText size="xs" weight="700" tone="muted">
            {g.kindLabel}
          </AppText>
          {g.items.map((c) => (
            <Row key={c.id} label={c.name} value={subscribed.has(c.id)} onValueChange={() => toggleCat(c.id)} compact />
          ))}
        </View>
      ))}

      <View style={{ gap: theme.space(1.5) }}>
        <AppText size="xs" weight="700" tone="faint" style={{ letterSpacing: 0.6 }}>
          DELIVERY
        </AppText>
        <Segmented
          value={prefs.digestMode}
          onChange={(v) => patch({ digestMode: v as NotificationPreferences["digestMode"] })}
          options={[
            { value: "OFF", label: "Instant" },
            { value: "DAILY", label: "Daily" },
            { value: "WEEKLY", label: "Weekly" },
          ]}
        />
        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
          <AppText size="sm" tone="muted">
            Max per week
          </AppText>
          <View style={{ flexDirection: "row", alignItems: "center", gap: theme.space(3) }}>
            <Stepper
              value={prefs.promoFrequencyCapPerWeek}
              onChange={(n) => patch({ promoFrequencyCapPerWeek: n })}
            />
          </View>
        </View>
      </View>
    </Card>
  );
}

function Row({
  label, hint, value, onValueChange, compact,
}: {
  label: string; hint?: string; value: boolean; onValueChange: (v: boolean) => void; compact?: boolean;
}) {
  const { theme } = useTheme();
  return (
    <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(3) }}>
      <View style={{ flexShrink: 1 }}>
        <AppText size={compact ? "sm" : "md"}>{label}</AppText>
        {hint ? (
          <AppText size="xs" tone="faint">
            {hint}
          </AppText>
        ) : null}
      </View>
      <Switch value={value} onValueChange={onValueChange} trackColor={{ true: theme.color.primary }} />
    </View>
  );
}

function Stepper({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const { theme } = useTheme();
  const btn = (label: string, delta: number) => (
    <Pressable
      onPress={() => onChange(Math.max(0, Math.min(30, value + delta)))}
      style={{
        width: 34, height: 34, borderRadius: theme.radius.sm, borderWidth: 1,
        borderColor: theme.color.border, alignItems: "center", justifyContent: "center",
      }}
    >
      <AppText weight="700">{label}</AppText>
    </Pressable>
  );
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: theme.space(2) }}>
      {btn("−", -1)}
      <AppText weight="700" style={{ minWidth: 20, textAlign: "center" }}>
        {value}
      </AppText>
      {btn("+", 1)}
    </View>
  );
}
