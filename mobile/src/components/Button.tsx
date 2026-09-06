import React from "react";
import { ActivityIndicator, Pressable, StyleProp, View, ViewStyle } from "react-native";
import { useTheme } from "@/theme/ThemeProvider";
import { AppText } from "./Themed";

type Variant = "primary" | "secondary" | "ghost" | "danger";

export function Button({
  label,
  onPress,
  variant = "primary",
  loading = false,
  disabled = false,
  fullWidth = true,
  style,
  left,
}: {
  label: string;
  onPress: () => void;
  variant?: Variant;
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: StyleProp<ViewStyle>;
  left?: React.ReactNode;
}) {
  const { theme } = useTheme();
  const isDisabled = disabled || loading;

  const bg =
    variant === "primary" ? theme.color.primary
    : variant === "danger" ? theme.color.danger
    : variant === "secondary" ? theme.color.surfaceAlt
    : "transparent";
  const fg =
    variant === "primary" ? theme.color.primaryText
    : variant === "danger" ? theme.color.dangerText
    : variant === "ghost" ? theme.color.primary
    : theme.color.text;

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        {
          backgroundColor: bg,
          borderRadius: theme.radius.sm,
          paddingVertical: theme.space(3),
          paddingHorizontal: theme.space(4),
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "row",
          gap: theme.space(2),
          alignSelf: fullWidth ? "stretch" : "flex-start",
          borderWidth: variant === "secondary" ? 1 : 0,
          borderColor: theme.color.border,
          opacity: isDisabled ? 0.5 : pressed ? 0.85 : 1,
          minHeight: 44,
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <>
          {left ? <View>{left}</View> : null}
          <AppText style={{ color: fg }} weight="600">
            {label}
          </AppText>
        </>
      )}
    </Pressable>
  );
}
