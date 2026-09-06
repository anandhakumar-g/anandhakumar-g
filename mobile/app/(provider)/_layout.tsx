import { Tabs } from "expo-router";
import React from "react";
import type { ColorValue } from "react-native";
import { Icon, type IconName } from "@/components/Icon";
import { useTheme } from "@/theme/ThemeProvider";

const tabIcon =
  (name: IconName) =>
  ({ color }: { color: ColorValue }) =>
    <Icon name={name} color={color as string} size={22} />;

export default function ProviderLayout() {
  const { theme } = useTheme();
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
      <Tabs.Screen name="index" options={{ title: "Jobs", tabBarIcon: tabIcon("wrench") }} />
      <Tabs.Screen name="offers" options={{ title: "Offers", tabBarIcon: tabIcon("tag") }} />
      <Tabs.Screen name="kyc" options={{ title: "Verify", tabBarIcon: tabIcon("shield") }} />
      <Tabs.Screen name="settings" options={{ title: "Settings", tabBarIcon: tabIcon("settings") }} />
      <Tabs.Screen name="job/[id]" options={{ href: null }} />
    </Tabs>
  );
}
