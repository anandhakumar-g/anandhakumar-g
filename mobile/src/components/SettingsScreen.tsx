import Constants, { ExecutionEnvironment } from "expo-constants";
import * as Device from "expo-device";
import { useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import { Alert, Platform, Pressable, Switch, View } from "react-native";
import { catalog, me as meApi } from "@/api/endpoints";
import { DeviceSession, groupVendorCategories, NotificationPreferences, VendorCategory } from "@/api/types";
import { BillingCard } from "@/components/BillingCard";
import { Button } from "@/components/Button";
import { Divider, KeyValue, Segmented } from "@/components/Bits";
import { AppText, Card, Screen } from "@/components/Themed";
import { canDownloadCsv, downloadCsv } from "@/lib/download";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";
import { THEMES, THEME_LABELS, ThemeName } from "@/theme/tokens";

export function SettingsScreen({
  showOfferPrefs = false,
  showBilling = false,
  children,
}: {
  showOfferPrefs?: boolean;
  showBilling?: boolean;
  children?: React.ReactNode;
}) {
  const { theme, themeName, isExplicit, setTheme } = useTheme();
  const router = useRouter();
  const { me, user, signIn, signOut, refreshMe, biometricAvailable, biometricEnabled, setBiometricEnabled } = useSession();
  const [pushMsg, setPushMsg] = useState<string | null>(null);

  async function backToPlatform() {
    try {
      await signIn(await meApi.stopActing());
      router.replace("/(super)");
    } catch {
      /* best effort */
    }
  }

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
      // expo-notifications' remote-push API was removed from Expo Go in SDK 53 — importing it
      // there throws. Load it lazily and only outside Expo Go (a dev / standalone build).
      if (Constants.executionEnvironment === ExecutionEnvironment.StoreClient) {
        return setPushMsg("Push needs a development build — it isn't available in Expo Go.");
      }
      const Notifications = await import("expo-notifications");
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
        {user?.role === "RESIDENT" || user?.role === "ADMIN" ? <CommunityRow /> : (
          <KeyValue k="Community" v={me?.activeTenantBranding?.name} />
        )}
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

      {children}

      {showBilling && user?.role !== "SUPER_ADMIN" ? <BillingCard /> : null}

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

      <DevicesCard />

      <DataAndPrivacyCard />

      {biometricAvailable ? (
        <Card style={{ gap: theme.space(2) }}>
          <AppText size="sm" weight="700" tone="muted">
            Security
          </AppText>
          <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
            <View style={{ flex: 1, paddingRight: theme.space(2) }}>
              <AppText>Face ID / fingerprint unlock</AppText>
              <AppText size="xs" tone="faint">
                Require it to open the app
              </AppText>
            </View>
            <Switch
              value={biometricEnabled}
              onValueChange={setBiometricEnabled}
              trackColor={{ true: theme.color.primary }}
            />
          </View>
        </Card>
      ) : null}

      <Divider />
      {user?.role === "SUPER_ADMIN" ? (
        <Button label="← Back to platform" variant="secondary" onPress={backToPlatform} />
      ) : null}
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
        label="WhatsApp updates"
        hint="Also get ticket updates on WhatsApp (if your community's plan includes it)"
        value={prefs.whatsappEnabled}
        onValueChange={(v) => patch({ whatsappEnabled: v })}
      />
      <Row
        label="Promotional offers"
        hint="Deals from vendors you follow"
        value={prefs.promoNotificationsEnabled}
        onValueChange={(v) => patch({ promoNotificationsEnabled: v })}
      />
      <Row
        label="Offers on WhatsApp"
        hint="Also send those deals to WhatsApp (off by default)"
        value={prefs.promoWhatsappEnabled}
        onValueChange={(v) => patch({ promoWhatsappEnabled: v })}
      />
      <Row
        label="Community announcements"
        hint="Broadcasts from your admin and the platform"
        value={prefs.broadcastEnabled}
        onValueChange={(v) => patch({ broadcastEnabled: v })}
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

function DevicesCard() {
  const { theme } = useTheme();
  const [devices, setDevices] = useState<DeviceSession[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = () => meApi.devices().then(setDevices).catch(() => setDevices([]));
  useEffect(() => {
    load();
  }, []);

  async function run(key: string, fn: () => Promise<unknown>) {
    setBusy(key);
    try {
      await fn();
      await load();
    } finally {
      setBusy(null);
    }
  }

  if (!devices || devices.length === 0) return null;
  const others = devices.filter((d) => !d.current && !d.revoked);

  return (
    <Card style={{ gap: theme.space(2) }}>
      <AppText size="sm" weight="700" tone="muted">
        Devices
      </AppText>
      {devices.map((d) => (
        <View key={d.id} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: theme.space(2) }}>
          <View style={{ flexShrink: 1 }}>
            <AppText size="sm">
              {d.label ?? "Device"}
              {d.current ? "  ·  this device" : ""}
            </AppText>
            <AppText size="xs" tone="faint">
              {d.revoked ? "signed out" : `last seen ${new Date(d.lastSeenAt).toLocaleDateString()}`}
            </AppText>
          </View>
          {!d.current && !d.revoked ? (
            <AppText size="xs" tone="danger" onPress={() => run(d.id, () => meApi.revokeDevice(d.id))}>
              {busy === d.id ? "…" : "Sign out"}
            </AppText>
          ) : null}
        </View>
      ))}
      {others.length > 0 ? (
        <Button
          label="Sign out all other devices"
          variant="secondary"
          fullWidth={false}
          loading={busy === "others"}
          onPress={() => run("others", () => meApi.revokeOtherDevices())}
        />
      ) : null}
    </Card>
  );
}

function DataAndPrivacyCard() {
  const { theme } = useTheme();
  const { token, signOut } = useSession();
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function exportData() {
    setMsg(null);
    if (!canDownloadCsv) {
      setMsg("Open the app in a web browser to download your data.");
      return;
    }
    try {
      await downloadCsv("/me/export", token);
    } catch (e: any) {
      setMsg(e?.message ?? "Could not download");
    }
  }

  function confirmDelete() {
    Alert.alert(
      "Delete your account?",
      "Your name, email and phone are scrubbed and you're removed from every community. "
        + "This can't be undone. Open requests or unpaid bills will block it.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: async () => {
            setBusy(true);
            setMsg(null);
            try {
              await meApi.deleteAccount();
              await signOut();
            } catch (e: any) {
              setMsg(e?.message ?? "Could not delete the account");
            } finally {
              setBusy(false);
            }
          },
        },
      ],
    );
  }

  return (
    <Card style={{ gap: theme.space(2) }}>
      <AppText size="sm" weight="700" tone="muted">
        Your data
      </AppText>
      <Button label="Download my data" variant="secondary" onPress={exportData} />
      <Button label="Delete my account" variant="danger" loading={busy} onPress={confirmDelete} />
      {msg ? (
        <AppText size="xs" tone="danger">
          {msg}
        </AppText>
      ) : null}
    </Card>
  );
}

function CommunityRow() {
  const { theme } = useTheme();
  const router = useRouter();
  const { me, user, switchCommunity } = useSession();
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);

  const active = (me?.memberships ?? []).filter((m) => m.status === "ACTIVE");
  const isResident = user?.role === "RESIDENT";

  async function pick(tenantId: string) {
    if (tenantId === me?.activeTenantId) return setOpen(false);
    setBusy(true);
    try {
      await switchCommunity(tenantId);
      setOpen(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={{ gap: theme.space(1) }}>
      <Pressable
        onPress={() => setOpen((o) => !o)}
        style={{ flexDirection: "row", justifyContent: "space-between", paddingVertical: theme.space(1) }}
      >
        <AppText size="sm" tone="muted">Community</AppText>
        <AppText size="sm" weight="500" tone="primary">
          {me?.activeTenantBranding?.name ?? "—"} {open ? "▲" : "▾"}
        </AppText>
      </Pressable>
      {open ? (
        <View style={{ gap: theme.space(1) }}>
          {active.map((m) => (
            <Pressable
              key={m.tenantId}
              disabled={busy}
              onPress={() => pick(m.tenantId)}
              style={{
                flexDirection: "row", justifyContent: "space-between", alignItems: "center",
                padding: theme.space(2.5), borderRadius: theme.radius.sm, borderWidth: 1,
                borderColor: m.tenantId === me?.activeTenantId ? theme.color.primary : theme.color.border,
              }}
            >
              <AppText size="sm">{m.tenantName ?? m.tenantId}</AppText>
              <AppText size="xs" tone="faint">
                {m.flatLabel ? m.flatLabel + " · " : ""}{m.householdRole.toLowerCase()}
                {m.tenantId === me?.activeTenantId ? "  ✓" : ""}
              </AppText>
            </Pressable>
          ))}
          {isResident ? (
            <Button
              label="Manage / join communities"
              variant="ghost"
              onPress={() => { setOpen(false); router.push("/(resident)/community" as any); }}
            />
          ) : null}
        </View>
      ) : null}
    </View>
  );
}
