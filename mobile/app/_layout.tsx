import { useFonts } from "expo-font";
import { Stack, useRouter, useSegments } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import React, { useEffect } from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { BiometricLockScreen } from "@/components/BiometricLockScreen";
import { Loading } from "@/components/Themed";
import { SessionProvider, useSession } from "@/store/SessionProvider";
import { FONT_ASSETS } from "@/theme/fonts";
import { ThemeProvider, useTheme } from "@/theme/ThemeProvider";

SplashScreen.preventAutoHideAsync().catch(() => {});

function homeFor(role?: string | null, actingTenantId?: string | null): string {
  switch (role) {
    case "ADMIN":
      return "/(admin)";
    case "PROVIDER":
      return "/(provider)";
    case "SUPER_ADMIN":
      // A Super Admin who has assumed a community's scope works in the admin UI.
      return actingTenantId ? "/(admin)" : "/(super)";
    default:
      return "/(resident)";
  }
}

function Gate() {
  const { ready, token, user, onboardingState, locked } = useSession();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    const group = segments[0]; // "(auth)" | "(resident)" | ...

    if (!token) {
      if (group !== "(auth)") router.replace("/(auth)/login");
      return;
    }
    let target: string | null = null;
    if (onboardingState === "NEEDS_PROFILE") target = "/(auth)/profile";
    else if (onboardingState === "NEEDS_COMMUNITY") target = "/(auth)/join";
    else if (onboardingState === "PENDING_APPROVAL") target = "/(auth)/pending";
    else if (onboardingState === "READY") target = homeFor(user?.role, user?.activeTenantId);

    if (!target) return;
    const targetGroup = target.match(/\(([^)]+)\)/)?.[0];
    // A Super Admin may move freely between (super) and (admin) once past onboarding.
    if (user?.role === "SUPER_ADMIN" && (group === "(super)" || group === "(admin)")) return;
    if (group !== targetGroup) router.replace(target as any);
  }, [ready, token, user?.role, user?.activeTenantId, onboardingState, segments, router]);

  if (!ready) return <Loading label="Starting Single Point…" />;
  if (locked) return <BiometricLockScreen />;
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: "transparent" } }}>
      <Stack.Screen name="(auth)" />
      <Stack.Screen name="(resident)" />
      <Stack.Screen name="(admin)" />
      <Stack.Screen name="(provider)" />
      <Stack.Screen name="(super)" />
    </Stack>
  );
}

function ThemedStatusBar() {
  const { theme } = useTheme();
  return <StatusBar style={theme.dark ? "light" : "dark"} />;
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(FONT_ASSETS);

  useEffect(() => {
    if (fontsLoaded || fontError) SplashScreen.hideAsync().catch(() => {});
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <SessionProvider>
            <ThemedStatusBar />
            <Gate />
          </SessionProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
