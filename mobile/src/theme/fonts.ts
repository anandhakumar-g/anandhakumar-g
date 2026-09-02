/**
 * Custom typefaces: Bricolage Grotesque for display / headings, Plus Jakarta Sans for body.
 * Each weight is a distinct face (React Native can't synthesise weights from a variable font),
 * so screens select a family name rather than a numeric fontWeight — see AppText.
 */
import {
  BricolageGrotesque_400Regular,
  BricolageGrotesque_500Medium,
  BricolageGrotesque_600SemiBold,
  BricolageGrotesque_700Bold,
} from "@expo-google-fonts/bricolage-grotesque";
import {
  PlusJakartaSans_400Regular,
  PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold,
  PlusJakartaSans_700Bold,
} from "@expo-google-fonts/plus-jakarta-sans";

/** Map passed to expo-font's useFonts(). Keys become the registered fontFamily names. */
export const FONT_ASSETS = {
  BricolageGrotesque_400Regular,
  BricolageGrotesque_500Medium,
  BricolageGrotesque_600SemiBold,
  BricolageGrotesque_700Bold,
  PlusJakartaSans_400Regular,
  PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold,
  PlusJakartaSans_700Bold,
};

export type FontRole = "body" | "display";
export type FontWeightKey = "400" | "500" | "600" | "700";

export interface FontFamilies {
  body: Record<FontWeightKey, string>;
  display: Record<FontWeightKey, string>;
}

export const FONT_FAMILIES: FontFamilies = {
  body: {
    "400": "PlusJakartaSans_400Regular",
    "500": "PlusJakartaSans_500Medium",
    "600": "PlusJakartaSans_600SemiBold",
    "700": "PlusJakartaSans_700Bold",
  },
  display: {
    "400": "BricolageGrotesque_400Regular",
    "500": "BricolageGrotesque_500Medium",
    "600": "BricolageGrotesque_600SemiBold",
    "700": "BricolageGrotesque_700Bold",
  },
};
