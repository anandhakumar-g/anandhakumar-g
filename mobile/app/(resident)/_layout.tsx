import { Tabs } from "expo-router";
import React from "react";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

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
      <Tabs.Screen name="index" options={{ title: "Home" }} />
      <Tabs.Screen name="tickets" options={{ title: "Tickets" }} />
      <Tabs.Screen name="deals" options={{ title: "Deals" }} />
      <Tabs.Screen
        name="notifications"
        options={{ title: "Alerts", tabBarBadge: unread > 0 ? (unread > 99 ? "99+" : unread) : undefined }}
      />
      <Tabs.Screen name="settings" options={{ title: "Settings" }} />
      <Tabs.Screen name="raise" options={{ href: null }} />
      <Tabs.Screen name="ticket/[id]" options={{ href: null }} />
      <Tabs.Screen name="offer/[id]" options={{ href: null }} />
      <Tabs.Screen name="community" options={{ href: null }} />
      <Tabs.Screen name="household" options={{ href: null }} />
      <Tabs.Screen name="register-community" options={{ href: null }} />
    </Tabs>
  );
}
