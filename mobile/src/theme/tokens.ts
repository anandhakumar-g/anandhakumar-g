/**
 * Token-based design system. Every screen styles itself from these semantic tokens, so a new
 * theme is a config addition here — never a screen rewrite. (blueprint 4.15)
 */
import { FONT_FAMILIES, FontFamilies } from "./fonts";

export type ThemeName = "light" | "dark" | "ocean" | "sunset" | "forest";

export interface ThemeTokens {
  name: ThemeName;
  dark: boolean;
  color: {
    bg: string;
    surface: string;
    surfaceAlt: string;
    text: string;
    textMuted: string;
    textFaint: string;
    border: string;
    primary: string;
    primaryText: string;
    accent: string;
    danger: string;
    dangerText: string;
    success: string;
    warning: string;
    info: string;
    overlay: string;
  };
  space: (n: number) => number;
  radius: { sm: number; md: number; lg: number; pill: number };
  font: { xs: number; sm: number; md: number; lg: number; xl: number; xxl: number };
  type: FontFamilies;
}

const scale = (n: number) => n * 4;

const shared = {
  space: scale,
  radius: { sm: 8, md: 10, lg: 16, pill: 999 },
  font: { xs: 12.5, sm: 14, md: 15.5, lg: 20, xl: 27, xxl: 34 },
  type: FONT_FAMILIES,
};

export const THEMES: Record<ThemeName, ThemeTokens> = {
  light: {
    name: "light",
    dark: false,
    ...shared,
    color: {
      bg: "#F5F6F8",
      surface: "#FFFFFF",
      surfaceAlt: "#EEF0F4",
      text: "#171A20",
      textMuted: "#4A515E",
      textFaint: "#8A92A2",
      border: "#E3E6EC",
      primary: "#4F46E5",
      primaryText: "#FFFFFF",
      accent: "#6366F1",
      danger: "#B42318",
      dangerText: "#FFFFFF",
      success: "#15803D",
      warning: "#B45309",
      info: "#0E7490",
      overlay: "rgba(20,25,35,0.45)",
    },
  },
  dark: {
    name: "dark",
    dark: true,
    ...shared,
    color: {
      bg: "#0E1017",
      surface: "#171A22",
      surfaceAlt: "#20242F",
      text: "#ECEEF3",
      textMuted: "#A7AEBD",
      textFaint: "#6B7382",
      border: "#282D39",
      primary: "#8B84FF",
      primaryText: "#12101F",
      accent: "#A5A0FF",
      danger: "#E5796E",
      dangerText: "#1A0F0D",
      success: "#56C878",
      warning: "#E0A44C",
      info: "#4FC5DB",
      overlay: "rgba(0,0,0,0.62)",
    },
  },
  // The three optional skins follow the same token contract as light/dark; faint
  // and muted text are tuned to clear WCAG AA on each skin's own ground.
  ocean: {
    name: "ocean",
    dark: false,
    ...shared,
    color: {
      bg: "#EAF6F7",
      surface: "#FFFFFF",
      surfaceAlt: "#DBEDEF",
      text: "#0A2E33",
      textMuted: "#37585C",
      textFaint: "#577478",
      border: "#CFE4E6",
      primary: "#0E7490",
      primaryText: "#FFFFFF",
      accent: "#0E7490",
      danger: "#B42318",
      dangerText: "#FFFFFF",
      success: "#15803D",
      warning: "#B45309",
      info: "#0E7490",
      overlay: "rgba(8,47,51,0.45)",
    },
  },
  sunset: {
    name: "sunset",
    dark: false,
    ...shared,
    color: {
      bg: "#FFF6EF",
      surface: "#FFFFFF",
      surfaceAlt: "#FBE7D8",
      text: "#3A2016",
      textMuted: "#6E4636",
      textFaint: "#8E6250",
      border: "#F1D9C8",
      primary: "#C2410C",
      primaryText: "#FFFFFF",
      accent: "#C2410C",
      danger: "#B42318",
      dangerText: "#FFFFFF",
      success: "#15803D",
      warning: "#B45309",
      info: "#0E7490",
      overlay: "rgba(58,32,22,0.45)",
    },
  },
  forest: {
    name: "forest",
    dark: true,
    ...shared,
    color: {
      bg: "#0C1A12",
      surface: "#13251A",
      surfaceAlt: "#1C3625",
      text: "#E9F5EC",
      textMuted: "#A9C7B4",
      textFaint: "#7FA089",
      border: "#24422F",
      primary: "#34D399",
      primaryText: "#06231A",
      accent: "#86EFAC",
      danger: "#E5796E",
      dangerText: "#1A0F0D",
      success: "#56C878",
      warning: "#E0A44C",
      info: "#4FC5DB",
      overlay: "rgba(0,0,0,0.62)",
    },
  },
};

export const THEME_LABELS: Record<ThemeName, string> = {
  light: "Daylight",
  dark: "Midnight",
  ocean: "Ocean",
  sunset: "Sunset",
  forest: "Forest",
};

export const statusColor = (t: ThemeTokens, status: string): string => {
  switch (status) {
    case "NEW":
    case "REOPENED":
      return t.color.info;
    case "ACKNOWLEDGED":
    case "ASSIGNED":
      return t.color.warning;
    case "ACCEPTED":
    case "IN_PROGRESS":
      return t.color.primary;
    case "ON_HOLD":
      return t.color.textFaint;
    case "RESOLVED":
      return t.color.success;
    case "CLOSED":
      return t.color.textMuted;
    case "REJECTED":
      return t.color.danger;
    default:
      return t.color.textMuted;
  }
};
