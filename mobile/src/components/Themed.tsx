import React from "react";
import {
  ActivityIndicator, RefreshControl, ScrollView, StyleProp, Text, TextProps, View, ViewStyle,
} from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useTheme } from "@/theme/ThemeProvider";

type TextTone = "default" | "muted" | "faint" | "primary" | "danger" | "success";
type TextSize = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";
type TextWeight = "400" | "500" | "600" | "700";
type FontRole = "body" | "display";

export function AppText({
  tone = "default",
  size = "md",
  weight = "400",
  family,
  style,
  ...rest
}: TextProps & {
  tone?: TextTone;
  size?: TextSize;
  weight?: TextWeight;
  /** Force a typeface role; defaults to display for lg/xl/xxl or bold text, body otherwise. */
  family?: FontRole;
}) {
  const { theme } = useTheme();
  const color =
    tone === "muted" ? theme.color.textMuted
    : tone === "faint" ? theme.color.textFaint
    : tone === "primary" ? theme.color.primary
    : tone === "danger" ? theme.color.danger
    : tone === "success" ? theme.color.success
    : theme.color.text;

  const role: FontRole =
    family ?? (size === "lg" || size === "xl" || size === "xxl" || weight === "700" ? "display" : "body");
  const fontFamily = theme.type[role][weight];
  const fontSize = theme.font[size];
  const isHeading = role === "display";

  return (
    <Text
      {...rest}
      style={[
        {
          color,
          fontFamily,
          fontSize,
          lineHeight: Math.round(fontSize * (isHeading ? 1.18 : 1.44)),
          letterSpacing: isHeading ? (fontSize >= 26 ? -0.7 : -0.3) : 0,
        },
        style,
      ]}
    />
  );
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

/**
 * The standard screen frame. Handles safe-area padding (status bar / notch at the top, the
 * tab bar at the bottom) so no screen hand-rolls insets, and carries optional {@code loading}
 * / {@code error} / {@code empty} slots so a screen renders its waiting states *inside* the
 * frame instead of returning a bare {@code <Loading/>} that loses the safe area.
 */
export function Screen({
  children,
  scroll = true,
  onRefresh,
  refreshing = false,
  contentStyle,
  loading = false,
  error,
  empty,
}: {
  children?: React.ReactNode;
  scroll?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
  loading?: boolean;
  error?: string | null;
  empty?: { title: string; body?: string } | null;
}) {
  const { theme } = useTheme();
  const insets = useSafeAreaInsets();
  const pad = { padding: theme.space(4), gap: theme.space(3) as number };

  let slot: React.ReactNode = null;
  if (loading) {
    slot = <ActivityIndicator color={theme.color.primary} size="large" />;
  } else if (error) {
    slot = (
      <AppText tone="danger" weight="600" style={{ textAlign: "center" }}>
        {error}
      </AppText>
    );
  } else if (empty) {
    slot = (
      <>
        <AppText size="lg" weight="700">
          {empty.title}
        </AppText>
        {empty.body ? (
          <AppText tone="muted" style={{ textAlign: "center", maxWidth: 320 }}>
            {empty.body}
          </AppText>
        ) : null}
      </>
    );
  }

  const centered = slot != null;
  const body = centered ? (
    <View style={{ flex: 1, minHeight: 260, alignItems: "center", justifyContent: "center", gap: theme.space(3) }}>
      {slot}
    </View>
  ) : (
    children
  );

  if (!scroll) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.color.bg }} edges={["top", "bottom"]}>
        <View style={[{ flex: 1 }, pad, contentStyle]}>{body}</View>
      </SafeAreaView>
    );
  }
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.color.bg }} edges={["top"]}>
      <ScrollView
        contentContainerStyle={[
          pad,
          { paddingBottom: theme.space(12) + insets.bottom },
          centered ? { flexGrow: 1 } : null,
          contentStyle,
        ]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          onRefresh ? (
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.color.primary} />
          ) : undefined
        }
      >
        {body}
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
