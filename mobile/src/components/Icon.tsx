import React from "react";
import { AppText } from "./Themed";

/**
 * MVP-12 (D): the ~dozen glyphs the app uses, in one place, so nav / buttons / rows render
 * them at a consistent weight and size. Text glyphs for now (no icon font dependency); the
 * call sites don't change when that swaps.
 */
const GLYPHS = {
  add: "＋",
  back: "‹",
  forward: "›",
  arrowRight: "→",
  star: "★",
  starOutline: "☆",
  check: "✓",
  close: "✕",
  lock: "🔒",
  megaphone: "📣",
  pin: "📍",
  bell: "🔔",
  download: "⤓",
} as const;

export type IconName = keyof typeof GLYPHS;

type AppTextProps = React.ComponentProps<typeof AppText>;

export function Icon({
  name,
  size = "md",
  tone,
  weight = "600",
  style,
}: {
  name: IconName;
  size?: AppTextProps["size"];
  tone?: AppTextProps["tone"];
  weight?: AppTextProps["weight"];
  style?: AppTextProps["style"];
}) {
  return (
    <AppText size={size} tone={tone} weight={weight} style={style} accessibilityElementsHidden>
      {GLYPHS[name]}
    </AppText>
  );
}
