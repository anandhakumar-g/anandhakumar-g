import * as ImagePicker from "expo-image-picker";
import * as Location from "expo-location";
import { Stack, useRouter } from "expo-router";
import React, { useEffect, useState } from "react";
import { Image, Pressable, View } from "react-native";
import { catalog, me as meApi, tickets } from "@/api/endpoints";
import { Category, MyFlat, PublicProviderView } from "@/api/types";
import { Button } from "@/components/Button";
import { Divider, Segmented } from "@/components/Bits";
import { DirectProviderPicker } from "@/components/DirectProviderPicker";
import { Field } from "@/components/Field";
import { AppText, Card, Screen } from "@/components/Themed";
import { useAsync } from "@/hooks/useAsync";
import { useSession } from "@/store/SessionProvider";
import { useTheme } from "@/theme/ThemeProvider";

type Pick = { uri: string; name: string; type: string };

export default function Raise() {
  const { theme } = useTheme();
  const router = useRouter();
  const { me } = useSession();
  const cats = useAsync(() => catalog.categories(), []);
  const flats = useAsync(() => meApi.flats(), []);

  const [category, setCategory] = useState<Category | null>(null);
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<"LOW" | "NORMAL" | "HIGH" | "URGENT">("NORMAL");
  const [flatId, setFlatId] = useState<string | null>(null);
  const [address, setAddress] = useState("");
  const [landmark, setLandmark] = useState("");
  const [geo, setGeo] = useState<{ lat: number; lng: number } | null>(null);
  const [photos, setPhotos] = useState<Pick[]>([]);
  const [provider, setProvider] = useState<PublicProviderView | null>(null);
  const [pickingProvider, setPickingProvider] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const myFlats: MyFlat[] = flats.data ?? [];
  // auto-select when there's exactly one flat
  useEffect(() => {
    if (myFlats.length === 1 && !flatId) {
      setFlatId(myFlats[0].flatId);
      if (myFlats[0].label && !address) setAddress(myFlats[0].label);
    }
  }, [flats.data]); // eslint-disable-line react-hooks/exhaustive-deps

  const flatRequired = myFlats.length > 0;
  // MVP-8: a community-less individual can only book a provider directly.
  const noCommunity = !me?.activeTenantId;
  const providerRequired = noCommunity;

  async function addPhoto() {
    try {
      const res = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ["images", "videos"], quality: 0.7 });
      if (!res.canceled && res.assets?.[0]) {
        const a = res.assets[0];
        setPhotos((p) => [...p, {
          uri: a.uri,
          name: a.fileName ?? `attachment-${p.length + 1}.jpg`,
          type: a.mimeType ?? (a.type === "video" ? "video/mp4" : "image/jpeg"),
        }]);
      }
    } catch {
      setError("Could not open the photo library");
    }
  }

  async function useMyLocation() {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") return setError("Location permission denied");
      const pos = await Location.getCurrentPositionAsync({});
      setGeo({ lat: pos.coords.latitude, lng: pos.coords.longitude });
    } catch {
      setError("Could not get your location");
    }
  }

  async function submit() {
    if (!category) return;
    if (flatRequired && !flatId) return setError("Choose which flat this is for");
    if (providerRequired && !provider) return setError("Pick a provider — you're not in a community");
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
        flatId: flatId ?? undefined,
        providerId: provider?.id,
      });
      for (const p of photos) {
        try {
          await tickets.upload(t.id, p);
        } catch {
          /* keep going — the ticket exists */
        }
      }
      router.replace(`/(resident)/ticket/${t.id}`);
    } catch (e: any) {
      setError(e.message ?? "Could not raise the request");
    } finally {
      setBusy(false);
    }
  }

  if (cats.loading) return <Screen loading />;

  return (
    <Screen>
      <Stack.Screen options={{ headerShown: true, title: category ? category.name : "New request" }} />

      {!category ? (
        <>
          <AppText size="lg" weight="700">What's it about?</AppText>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
            {cats.data?.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => setCategory(c)}
                style={{
                  width: "48%", minHeight: 84, backgroundColor: theme.color.surface,
                  borderWidth: 1, borderColor: theme.color.border, borderRadius: theme.radius.md,
                  padding: theme.space(3.5), justifyContent: "center",
                }}
              >
                <AppText weight="700">{c.name}</AppText>
                <AppText size="xs" tone="faint">{c.requestType.toLowerCase()}</AppText>
              </Pressable>
            ))}
          </View>
        </>
      ) : (
        <>
          {myFlats.length > 1 ? (
            <View style={{ gap: theme.space(1.5) }}>
              <AppText size="sm" weight="600" tone="muted">Which flat is this for?</AppText>
              {myFlats.map((f) => (
                <Pressable
                  key={f.flatId}
                  onPress={() => { setFlatId(f.flatId); if (f.label) setAddress(f.label); }}
                  style={{
                    flexDirection: "row", justifyContent: "space-between", alignItems: "center",
                    padding: theme.space(3), borderRadius: theme.radius.sm, borderWidth: 1,
                    borderColor: flatId === f.flatId ? theme.color.primary : theme.color.border,
                  }}
                >
                  <AppText weight={flatId === f.flatId ? "700" : "500"}>{f.label ?? f.flatId}</AppText>
                  <AppText size="xs" tone="faint">{f.householdRole.toLowerCase()}</AppText>
                </Pressable>
              ))}
            </View>
          ) : null}

          <Field
            label="Describe it"
            placeholder="What's happening? Where exactly?"
            value={description}
            onChangeText={setDescription}
            multiline
            numberOfLines={4}
            style={{ minHeight: 100, textAlignVertical: "top" }}
          />

          <View style={{ gap: theme.space(1.5) }}>
            <AppText size="sm" weight="600" tone="muted">Priority</AppText>
            <Segmented
              value={priority}
              onChange={setPriority}
              options={[
                { value: "LOW", label: "Low" }, { value: "NORMAL", label: "Normal" },
                { value: "HIGH", label: "High" }, { value: "URGENT", label: "Urgent" },
              ]}
            />
          </View>

          <Card style={{ gap: theme.space(2) }}>
            <AppText size="sm" weight="700">Photos / video</AppText>
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

          {me?.directServiceEnabled || noCommunity ? (
            <Card style={{ gap: theme.space(2) }}>
              <AppText size="sm" weight="700">
                {noCommunity ? "Choose a provider" : "Book a provider directly?"}
              </AppText>
              {provider ? (
                <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                  <View>
                    <AppText weight="600">{provider.name}</AppText>
                    <AppText size="xs" tone="faint">{provider.vendorCategoryLabel ?? "Provider"}</AppText>
                  </View>
                  {noCommunity ? (
                    <AppText size="sm" tone="primary" onPress={() => setPickingProvider(true)}>Change</AppText>
                  ) : (
                    <AppText size="sm" tone="primary" onPress={() => setProvider(null)}>Send to community instead</AppText>
                  )}
                </View>
              ) : pickingProvider ? (
                <DirectProviderPicker
                  onCancel={() => setPickingProvider(false)}
                  onPick={(p) => { setProvider(p); setPickingProvider(false); }}
                />
              ) : (
                <>
                  <AppText size="xs" tone="faint">
                    {noCommunity
                      ? "Your request goes straight to the verified provider you pick."
                      : "Skip the community queue — send this straight to a verified provider."}
                  </AppText>
                  <Button label="Choose a provider" variant="secondary" onPress={() => setPickingProvider(true)} />
                </>
              )}
            </Card>
          ) : null}

          {error ? <AppText tone="danger" size="sm">{error}</AppText> : null}
          <Divider />
          <Button
            label={provider ? `Book ${provider.name}` : "Submit request"}
            onPress={submit}
            loading={busy}
            disabled={
              description.trim().length < 5 ||
              (flatRequired && !flatId) ||
              (providerRequired && !provider)
            }
          />
          <Button label="Change category" variant="ghost" onPress={() => setCategory(null)} />
        </>
      )}
    </Screen>
  );
}
