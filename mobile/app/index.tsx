import { Redirect } from "expo-router";
import React from "react";
import { Loading } from "@/components/Themed";
import { useSession } from "@/store/SessionProvider";

export default function Index() {
  const { ready, token } = useSession();
  if (!ready) return <Loading />;
  if (!token) return <Redirect href="/(auth)/login" />;
  // The Gate in _layout handles onboarding/role routing once /me resolves.
  return <Loading label="Loading your account…" />;
}
