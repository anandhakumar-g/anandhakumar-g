import React from "react";
import type { StyleProp, TextStyle } from "react-native";
import {
  ArrowRight,
  Bell,
  Building2,
  Calendar,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  CreditCard,
  Download,
  FileText,
  Gauge,
  House,
  Layers,
  LayoutGrid,
  List,
  Lock,
  type LucideProps,
  MapPin,
  Megaphone,
  Plus,
  Search,
  Settings,
  Shield,
  Smartphone,
  Star,
  Tag,
  Ticket,
  User,
  Users,
  Wrench,
  X,
} from "lucide-react-native";
import { useTheme } from "@/theme/ThemeProvider";

/**
 * One icon set for the whole app — Lucide, on a 24px grid at a consistent stroke.
 * Keeps the small <Icon name size tone /> surface so call sites don't care that the
 * glyphs used to be text. (design system v2)
 */
const MAP = {
  add: Plus,
  back: ChevronLeft,
  forward: ChevronRight,
  arrowRight: ArrowRight,
  star: Star,
  starOutline: Star,
  check: Check,
  close: X,
  lock: Lock,
  megaphone: Megaphone,
  pin: MapPin,
  bell: Bell,
  download: Download,
  home: House,
  ticket: Ticket,
  tag: Tag,
  user: User,
  users: Users,
  settings: Settings,
  wrench: Wrench,
  shield: Shield,
  card: CreditCard,
  clock: Clock,
  device: Smartphone,
  calendar: Calendar,
  file: FileText,
  list: List,
  search: Search,
  building: Building2,
  layers: Layers,
  grid: LayoutGrid,
  gauge: Gauge,
} satisfies Record<string, React.ComponentType<LucideProps>>;

export type IconName = keyof typeof MAP;

const SIZES = { xs: 14, sm: 16, md: 18, lg: 22, xl: 28, xxl: 34 } as const;
const STROKE: Record<string, number> = { "400": 1.5, "500": 1.6, "600": 1.75, "700": 2 };

type Tone = "default" | "muted" | "faint" | "primary" | "danger" | "success";

export function Icon({
  name,
  size = "md",
  tone,
  weight = "600",
  color,
  style,
}: {
  name: IconName;
  size?: keyof typeof SIZES | number;
  tone?: Tone;
  weight?: "400" | "500" | "600" | "700";
  color?: string;
  style?: StyleProp<TextStyle>;
}) {
  const { theme } = useTheme();
  const Glyph = MAP[name];
  const px = typeof size === "number" ? size : SIZES[size];
  const resolved =
    color ??
    (tone === "muted" ? theme.color.textMuted
      : tone === "faint" ? theme.color.textFaint
      : tone === "primary" ? theme.color.primary
      : tone === "danger" ? theme.color.danger
      : tone === "success" ? theme.color.success
      : theme.color.text);
  return (
    <Glyph
      size={px}
      color={resolved}
      strokeWidth={STROKE[weight]}
      fill={name === "star" ? resolved : "none"}
      style={style as never}
    />
  );
}
