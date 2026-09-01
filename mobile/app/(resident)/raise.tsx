import * as ImagePicker from "expo-image-picker";
import * as Location from "expo-location";
import { Stack, useRouter } from "expo-router";
import React, { useState } from "react";
import { Image, Pressable, View } from "react-native";
import { catalog, tickets } from "@/api/endpoints";
import { Category } from "@/api/types";
import { Button } from "@/components/Button";
import { Divider, Segmented } from "@/components/Bits";
import { Field } from "@/components/Field";
import { AppText, Card, Loading, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

type Pick = { uri: string; name: string; type: string };

export default function Raise() {
  const { theme } = useTheme();
  const router = useRouter();
  const { me } = useSession();
  const cats = useAsync(() => catalog.categories(), []);

  const [category, setCategory] = useState<Category | null>(null);
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<"LOW" | "NORMAL" | "HIGH" | "URGENT">("NORMAL");
  const flat = me?.memberships.find((m) => m.status === "ACTIVE");
  const [address, setAddress] = useState(flat?.flatLabel ?? "");
  const [landmark, setLandmark] = useState("");
  const [geo, setGeo] = useState<{ lat: number; lng: number } | null>(null);
  const [photos, setPhotos] = useState<Pick[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function addPhoto() {
    try {
      const res = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ["images", "videos"],
        quality: 0.7,
      });
      if (!res.canceled && res.assets?.[0]) {
        const a = res.assets[0];
        setPhotos((p) => [
          ...p,
          {
            uri: a.uri,
            name: a.fileName ?? `attachment-${p.length + 1}.jpg`,
            type: a.mimeType ?? (a.type === "video" ? "video/mp4" : "image/jpeg"),
          },
        ]);
      }
    } catch (e: any) {
      setError("Could not open the photo library");
    }
  }

  async function useMyLocation() {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") {
        setError("Location permission denied");
        return;
      }
      const pos = await Location.getCurrentPositionAsync({});
      setGeo({ lat: pos.coords.latitude, lng: pos.coords.longitude });
    } catch {
      setError("Could not get your location");
    }
  }

  async function submit() {
    if (!category) return;
    setBusy(true);
    setError(null);
    try {
      const t = await tickets.raise({
        categoryId: category.id,
        description: description.trim(),
        priority,
        serviceAddressText: address.trim() || undefined,
        serviceGeoLat: geo?.lat,
        serviceGeoLng: geo?.lng,
        serviceLandmark: landmark.trim() || undefined,
        flatId: flat?.flatId ?? undefined,
      });
      for (const p of photos) {
        try {
          await tickets.upload(t.id, p);
        } catch {
          /* keep going — the ticket is already created */
        }
      }
      router.replace(`/(resident)/ticket/${t.id}`);
    } catch (e: any) {
      setError(e.message ?? "Could not raise the ticket");
    } finally {
      setBusy(false);
    }
  }

  if (cats.loading) return <Loading label="Loading categories…" />;

  return (
    <Screen>
      <Stack.Screen options={{ headerShown: true, title: category ? category.name : "New ticket" }} />

      {!category ? (
        <>
          <AppText size="lg" weight="700">
            What's it about?
          </AppText>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
            {cats.data?.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => setCategory(c)}
                style={{
                  width: "48%",
                  minHeight: 84,
                  backgroundColor: theme.color.surface,
                  borderWidth: 1,
                  borderColor: theme.color.border,
                  borderRadius: theme.radius.md,
                  padding: theme.space(3.5),
                  justifyContent: "center",
                }}
              >
                <AppText weight="700">{c.name}</AppText>
                <AppText size="xs" tone="faint">
                  {c.requestType.toLowerCase()}
                </AppText>
              </Pressable>
            ))}
          </View>
        </>
      ) : (
        <>
          <Field
            label="Describe the issue"
            placeholder="What's happening? Where exactly?"
            value={description}
            onChangeText={setDescription}
            multiline
            numberOfLines={4}
            style={{ minHeight: 100, textAlignVertical: "top" }}
          />

          <View style={{ gap: theme.space(1.5) }}>
            <AppText size="sm" weight="600" tone="muted">
              Priority
            </AppText>
            <Segmented
              value={priority}
              onChange={setPriority}
              options={[
                { value: "LOW", label: "Low" },
                { value: "NORMAL", label: "Normal" },
                { value: "HIGH", label: "High" },
                { value: "URGENT", label: "Urgent" },
              ]}
            />
          </View>

          <Card style={{ gap: theme.space(2) }}>
            <AppText size="sm" weight="700">
              Photos / video
            </AppText>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
              {photos.map((p, i) => (
                <Image
                  key={i}
                  source={{ uri: p.uri }}
                  style={{ width: 64, height: 64, borderRadius: theme.radius.sm, backgroundColor: theme.color.surfaceAlt }}
                />
              ))}
              <Button label="＋ Add" variant="secondary" fullWidth={false} onPress={addPhoto} />
            </View>
          </Card>

          <Field label="Service location" value={address} onChangeText={setAddress} placeholder="Flat / common area" />
          <Field label="Landmark (optional)" value={landmark} onChangeText={setLandmark} placeholder="e.g. near the lift" />
          <Button
            label={geo ? `📍 Location attached (${geo.lat.toFixed(4)}, ${geo.lng.toFixed(4)})` : "📍 Use my current location"}
            variant="ghost"
            onPress={useMyLocation}
          />

          {error ? (
            <AppText tone="danger" size="sm">
              {error}
            </AppText>
          ) : null}
          <Divider />
          <Button label="Submit ticket" onPress={submit} loading={busy} disabled={description.trim().length < 5} />
          <Button label="Change category" variant="ghost" onPress={() => setCategory(null)} />
        </>
      )}
    </Screen>
  );
}
