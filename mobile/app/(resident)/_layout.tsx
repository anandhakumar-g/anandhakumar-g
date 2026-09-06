import { Tabs } from "expo-router";
import React from "react";
import type { ColorValue } from "react-native";
import { Icon, type IconName } from "@/components/Icon";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

const tabIcon =
  (name: IconName) =>
  ({ color }: { color: ColorValue }) =>
    <Icon name={name} color={color as string} size={22} />;

export default function ResidentLayout() {
  const { theme } = useTheme();
  const { me } = useSession();
  const unread = me?.unreadNotifications ?? 0;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.color.primary,
        tabBarInactiveTintColor: theme.color.textFaint,
        tabBarStyle: { backgroundColor: theme.color.surface, borderTopColor: theme.color.border },
        tabBarLabelStyle: { fontSize: 12, fontWeight: "600" },
      }}
    >
      <Tabs.Screen name="index" options={{ title: "Home", tabBarIcon: tabIcon("home") }} />
      <Tabs.Screen name="tickets" options={{ title: "Tickets", tabBarIcon: tabIcon("ticket") }} />
      <Tabs.Screen name="deals" options={{ title: "Deals", tabBarIcon: tabIcon("tag") }} />
      <Tabs.Screen
        name="notifications"
        options={{
          title: "Alerts",
          tabBarIcon: tabIcon("bell"),
          tabBarBadge: unread > 0 ? (unread > 99 ? "99+" : unread) : undefined,
        }}
      />
      <Tabs.Screen name="settings" options={{ title: "Settings", tabBarIcon: tabIcon("settings") }} />
      <Tabs.Screen name="raise" options={{ href: null }} />
      <Tabs.Screen name="ticket/[id]" options={{ href: null }} />
      <Tabs.Screen name="offer/[id]" options={{ href: null }} />
      <Tabs.Screen name="community" options={{ href: null }} />
      <Tabs.Screen name="household" options={{ href: null }} />
      <Tabs.Screen name="register-community" options={{ href: null }} />
    </Tabs>
  );
}
