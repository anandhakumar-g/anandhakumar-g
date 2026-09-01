import React from "react";
import { TextInput, TextInputProps, View } from "react-native";
import { useTheme } from "@/theme/ThemeProvider";
import { AppText } from "./Themed";

export function Field({
  label,
  hint,
  error,
  style,
  ...rest
}: TextInputProps & { label?: string; hint?: string; error?: string | null }) {
  const { theme } = useTheme();
  return (
    <View style={{ gap: theme.space(1.5) }}>
      {label ? (
        <AppText size="sm" weight="600" tone="muted">
          {label}
        </AppText>
      ) : null}
      <TextInput
        placeholderTextColor={theme.color.textFaint}
        style={[
          {
            backgroundColor: theme.color.surface,
            borderWidth: 1,
            borderColor: error ? theme.color.danger : theme.color.border,
            borderRadius: theme.radius.md,
            paddingHorizontal: theme.space(3.5),
            paddingVertical: theme.space(3),
            color: theme.color.text,
            fontSize: theme.font.md,
            minHeight: 48,
          },
          style,
        ]}
        {...rest}
      />
      {error ? (
        <AppText size="xs" tone="danger">
          {error}
        </AppText>
      ) : hint ? (
        <AppText size="xs" tone="faint">
          {hint}
        </AppText>
      ) : null}
    </View>
  );
}
