import React from "react";
import {
  ActivityIndicator, RefreshControl, ScrollView, StyleProp, Text, TextProps, View, ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTheme } from "@/theme/ThemeProvider";

type TextTone = "default" | "muted" | "faint" | "primary" | "danger" | "success";
type TextSize = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";

export function AppText({
  tone = "default",
  size = "md",
  weight = "400",
  style,
  ...rest
}: TextProps & { tone?: TextTone; size?: TextSize; weight?: "400" | "500" | "600" | "700" }) {
  const { theme } = useTheme();
  const color =
    tone === "muted" ? theme.color.textMuted
    : tone === "faint" ? theme.color.textFaint
    : tone === "primary" ? theme.color.primary
    : tone === "danger" ? theme.color.danger
    : tone === "success" ? theme.color.success
    : theme.color.text;
  return <Text {...rest} style={[{ color, fontSize: theme.font[size], fontWeight: weight }, style]} />;
}

export function Card({
  children,
  style,
  padded = true,
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  padded?: boolean;
}) {
  const { theme } = useTheme();
  return (
    <View
      style={[
        {
          backgroundColor: theme.color.surface,
          borderRadius: theme.radius.md,
          borderWidth: 1,
          borderColor: theme.color.border,
          padding: padded ? theme.space(4) : 0,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function Screen({
  children,
  scroll = true,
  onRefresh,
  refreshing = false,
  contentStyle,
}: {
  children: React.ReactNode;
  scroll?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
}) {
  const { theme } = useTheme();
  const pad = { padding: theme.space(4), gap: theme.space(3) as number };
  if (!scroll) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.color.bg }} edges={["top", "bottom"]}>
        <View style={[{ flex: 1 }, pad, contentStyle]}>{children}</View>
      </SafeAreaView>
    );
  }
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.color.bg }} edges={["top"]}>
      <ScrollView
        contentContainerStyle={[pad, { paddingBottom: theme.space(12) }, contentStyle]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          onRefresh ? (
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.color.primary} />
          ) : undefined
        }
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Loading({ label }: { label?: string }) {
  const { theme } = useTheme();
  return (
    <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: theme.space(3), backgroundColor: theme.color.bg }}>
      <ActivityIndicator color={theme.color.primary} size="large" />
      {label ? <AppText tone="muted">{label}</AppText> : null}
    </View>
  );
}
