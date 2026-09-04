import { Tabs } from "expo-router";
import React from "react";
import { useTheme } from "@/theme/ThemeProvider";

export default function SuperLayout() {
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
      <Tabs.Screen name="index" options={{ title: "Platform" }} />
      <Tabs.Screen name="providers" options={{ title: "Providers" }} />
      <Tabs.Screen name="offers" options={{ title: "Offers" }} />
      <Tabs.Screen name="billing" options={{ title: "Billing" }} />
      <Tabs.Screen name="ticket-categories" options={{ title: "Categories" }} />
      <Tabs.Screen name="taxonomy" options={{ title: "Taxonomy" }} />
      <Tabs.Screen name="audit" options={{ href: null }} />
      <Tabs.Screen name="broadcast" options={{ href: null }} />
    </Tabs>
  );
}
