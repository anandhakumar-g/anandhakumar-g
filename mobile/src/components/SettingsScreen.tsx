import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import React, { useState } from "react";
import { Platform, Pressable, View } from "react-native";
import { me as meApi } from "@/api/endpoints";
import { Button } from "@/components/Button";
import { Divider, KeyValue } from "@/components/Bits";
import { AppText, Card, Screen } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";
import { THEMES, THEME_LABELS, ThemeName } from "@/theme/tokens";

export function SettingsScreen() {
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
      if (Platform.OS === "web") {
        setPushMsg("Push is only available in the mobile app.");
        return;
      }
      if (!Device.isDevice) {
        setPushMsg("Push needs a physical device.");
        return;
      }
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== "granted") {
        setPushMsg("Notification permission denied.");
        return;
      }
      const token = (await Notifications.getExpoPushTokenAsync()).data;
      await meApi.registerDevice(token, Platform.OS === "ios" ? "ios" : "android", "expo");
      setPushMsg("Push notifications enabled.");
    } catch (e: any) {
      setPushMsg("Could not enable push: " + (e?.message ?? "error"));
    }
  }

  return (
    <Screen onRefresh={refreshMe}>
      <AppText size="xl" weight="700">
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
                      width: 18,
                      height: 18,
                      borderRadius: 9,
                      backgroundColor: c,
                      marginLeft: i ? -6 : 0,
                      borderWidth: 1,
                      borderColor: theme.color.border,
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

      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">
          Notifications
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
