import { useFonts } from "expo-font";
import { Stack, useRouter, useSegments } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import React, { useEffect } from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { Loading } from "@/components/Themed";
import { SessionProvider, useSession } from "@/store/SessionProvider";
import { FONT_ASSETS } from "@/theme/fonts";
import { ThemeProvider, useTheme } from "@/theme/ThemeProvider";

SplashScreen.preventAutoHideAsync().catch(() => {});

function homeFor(role?: string | null): string {
  switch (role) {
    case "ADMIN":
      return "/(admin)";
    case "PROVIDER":
      return "/(provider)";
    case "SUPER_ADMIN":
      return "/(super)";
    default:
      return "/(resident)";
  }
}

function Gate() {
  const { ready, token, user, onboardingState } = useSession();
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
    else if (onboardingState === "READY") target = homeFor(user?.role);

    if (!target) return;
    const targetGroup = target.match(/\(([^)]+)\)/)?.[0];
    if (group !== targetGroup) router.replace(target as any);
  }, [ready, token, user?.role, onboardingState, segments, router]);

  if (!ready) return <Loading label="Starting Single Point…" />;
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
