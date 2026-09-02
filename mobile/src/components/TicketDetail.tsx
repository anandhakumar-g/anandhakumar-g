import { Stack, useRouter } from "expo-router";
import React, { useState } from "react";
import { Image, Linking, Platform, Pressable, View } from "react-native";
import { admin, catalog, tickets } from "@/api/endpoints";
import { ProviderView, Role, TicketView } from "@/api/types";
import { useAsync } from "@/hooks/useAsync";
import { useTheme } from "@/theme/ThemeProvider";
import { Button } from "./Button";
import { Divider, KeyValue, Pill, StatusBadge, Stars } from "./Bits";
import { Field } from "./Field";
import { PaymentPanel } from "./PaymentPanel";
import { AppText, Card, Loading, Screen } from "./Themed";

function mapsUrl(lat: number, lng: number, label?: string) {
  const q = `${lat},${lng}`;
  return Platform.select({
    ios: `maps:0,0?q=${label ? encodeURIComponent(label) + "@" : ""}${q}`,
    android: `geo:0,0?q=${q}(${encodeURIComponent(label ?? "Service location")})`,
    default: `https://www.google.com/maps/search/?api=1&query=${q}`,
  })!;
}

export function TicketDetail({ ticketId, role }: { ticketId: string; role: Role }) {
  const { theme } = useTheme();
  const router = useRouter();

  const cats = useAsync(() => catalog.categories(), []);
  const t = useAsync(() => tickets.get(ticketId), [ticketId]);
  const timeline = useAsync(() => tickets.timeline(ticketId), [ticketId]);
  const atts = useAsync(() => tickets.attachments(ticketId), [ticketId]);

  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [notes, setNotes] = useState("");
  const [reason, setReason] = useState("");
  const [rating, setRating] = useState(0);
  const [picker, setPicker] = useState<null | "assign" | "reroute">(null);

  async function act(fn: () => Promise<TicketView>) {
    setBusy(true);
    setErr(null);
    try {
      const updated = await fn();
      t.setData(updated);
      timeline.reload();
      setNotes("");
      setReason("");
    } catch (e: any) {
      setErr(e.message ?? "Action failed");
    } finally {
      setBusy(false);
    }
  }

  if (t.loading || !t.data) return <Loading />;
  const tk = t.data;
  const catName = cats.data?.find((c) => c.id === tk.categoryId)?.name;

  return (
    <Screen onRefresh={() => { t.reload(); timeline.reload(); atts.reload(); }} refreshing={t.refreshing}>
      <Stack.Screen options={{ headerShown: true, title: tk.referenceCode }} />

      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="lg" weight="700">
          {catName ?? "Ticket"}
        </AppText>
        <StatusBadge status={tk.status} />
      </View>
      <AppText>{tk.description}</AppText>

      <Card style={{ gap: theme.space(1) }}>
        <KeyValue k="Priority" v={tk.priority ?? "Normal"} />
        <KeyValue k="Location" v={tk.serviceAddressText} />
        <KeyValue k="Landmark" v={tk.serviceLandmark} />
        <KeyValue k="Preferred time" v={tk.preferredTimeWindow} />
        {tk.reopenedCount > 0 ? <KeyValue k="Reopened" v={`${tk.reopenedCount}×`} /> : null}
        {tk.serviceGeoLat != null && tk.serviceGeoLng != null ? (
          <Button
            label="🧭 Navigate"
            variant="ghost"
            onPress={() => Linking.openURL(mapsUrl(tk.serviceGeoLat!, tk.serviceGeoLng!, tk.serviceAddressText))}
          />
        ) : null}
      </Card>

      {tk.assignedProvider ? (
        <Card style={{ gap: theme.space(1.5) }}>
          <AppText size="sm" weight="700" tone="muted">
            Service provider
          </AppText>
          <AppText weight="700">{tk.assignedProvider.name}</AppText>
          <View style={{ flexDirection: "row", gap: theme.space(2) }}>
            {tk.assignedProvider.verificationStatus ? (
              <Pill
                text={tk.assignedProvider.verificationStatus === "VERIFIED" ? "✓ Verified" : tk.assignedProvider.verificationStatus}
                tone={tk.assignedProvider.verificationStatus === "VERIFIED" ? "success" : "muted"}
              />
            ) : null}
            {tk.assignedProvider.tier === "FEATURED" ? <Pill text="★ Featured" tone="primary" /> : null}
          </View>
          <KeyValue k="Contact" v={tk.assignedProvider.phone} />
        </Card>
      ) : null}

      {tk.raisedBy && role !== "RESIDENT" ? (
        <Card style={{ gap: theme.space(1) }}>
          <AppText size="sm" weight="700" tone="muted">
            Raised by
          </AppText>
          <KeyValue k="Resident" v={tk.raisedBy.name} />
          <KeyValue k="Contact" v={tk.raisedBy.phone} />
          <KeyValue k="Flat" v={tk.flatLabel} />
        </Card>
      ) : null}

      {(atts.data?.length ?? 0) > 0 ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
          {atts.data!.map((a) =>
            a.contentType.startsWith("image") ? (
              <Image
                key={a.id}
                source={{ uri: a.url }}
                style={{ width: 96, height: 96, borderRadius: theme.radius.sm, backgroundColor: theme.color.surfaceAlt }}
              />
            ) : (
              <Pressable key={a.id} onPress={() => Linking.openURL(a.url)}>
                <Pill text={a.originalFilename ?? "attachment"} />
              </Pressable>
            )
          )}
        </View>
      ) : null}

      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      <PaymentPanel ticketId={ticketId} role={role} ticketStatus={tk.status} />

      <ActionArea
        role={role}
        tk={tk}
        busy={busy}
        notes={notes}
        setNotes={setNotes}
        reason={reason}
        setReason={setReason}
        rating={rating}
        setRating={setRating}
        onAck={() => act(() => tickets.acknowledge(tk.id))}
        onResolveDirect={() => act(() => tickets.resolveDirect(tk.id, notes.trim()))}
        onAccept={() => act(() => tickets.accept(tk.id))}
        onReject={() => act(() => tickets.reject(tk.id, reason.trim() || undefined))}
        onStart={() => act(() => tickets.providerStatus(tk.id, "IN_PROGRESS"))}
        onHold={() => act(() => tickets.providerStatus(tk.id, "ON_HOLD", reason.trim() || undefined))}
        onResume={() => act(() => tickets.providerStatus(tk.id, "IN_PROGRESS"))}
        onProviderResolve={() => act(() => tickets.providerStatus(tk.id, "RESOLVED", undefined, notes.trim() || undefined))}
        onReopen={() => act(() => tickets.reopen(tk.id, reason.trim() || undefined))}
        onClose={() => act(() => tickets.close(tk.id, rating || undefined, notes.trim() || undefined))}
        onPickProvider={(mode: "assign" | "reroute") => setPicker(mode)}
      />

      {picker ? (
        <ProviderPicker
          onCancel={() => setPicker(null)}
          onPick={(p) => {
            setPicker(null);
            act(() => (picker === "assign" ? tickets.assign(tk.id, p.id) : tickets.reroute(tk.id, p.id)));
          }}
        />
      ) : null}

      <Divider />
      <AppText weight="700">Timeline</AppText>
      {(timeline.data ?? []).map((e, i) => (
        <View key={i} style={{ flexDirection: "row", gap: theme.space(3) }}>
          <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: theme.color.primary, marginTop: 6 }} />
          <View style={{ flex: 1 }}>
            <AppText size="sm" weight="600">
              {e.toStatus.replace(/_/g, " ")}
              <AppText size="xs" tone="faint">
                {"  "}· {e.actorRole?.toLowerCase()}
              </AppText>
            </AppText>
            {e.remarks ? (
              <AppText size="sm" tone="muted">
                {e.remarks}
              </AppText>
            ) : null}
            <AppText size="xs" tone="faint">
              {new Date(e.at).toLocaleString()}
            </AppText>
          </View>
        </View>
      ))}
    </Screen>
  );
}

function ActionArea(props: any) {
  const { theme } = useTheme();
  const { role, tk, busy } = props;
  const s = tk.status as string;

  if (role === "RESIDENT") {
    if (s === "RESOLVED") {
      return (
        <Card style={{ gap: theme.space(2) }}>
          <AppText weight="700">Was this fixed?</AppText>
          <Stars value={props.rating} onChange={props.setRating} />
          <Field placeholder="Add a comment (optional)" value={props.notes} onChangeText={props.setNotes} />
          <Button label="Confirm & close" onPress={props.onClose} loading={busy} />
          <Field placeholder="Why reopen? (optional)" value={props.reason} onChangeText={props.setReason} />
          <Button label="Reopen ticket" variant="secondary" onPress={props.onReopen} loading={busy} />
        </Card>
      );
    }
    return null;
  }

  if (role === "ADMIN") {
    const canReroute = ["ASSIGNED", "ACCEPTED", "IN_PROGRESS", "ON_HOLD", "REJECTED", "REOPENED"].includes(s);
    return (
      <Card style={{ gap: theme.space(2) }}>
        {s === "NEW" ? <Button label="Acknowledge" variant="secondary" onPress={props.onAck} loading={busy} /> : null}
        {(s === "NEW" || s === "ACKNOWLEDGED") ? (
          <>
            <Field placeholder="Resolution notes" value={props.notes} onChangeText={props.setNotes} />
            <Button label="Resolve directly" variant="secondary" onPress={props.onResolveDirect} loading={busy} disabled={props.notes.trim().length < 3} />
            <Button label="Assign to a provider" onPress={() => props.onPickProvider("assign")} loading={busy} />
          </>
        ) : null}
        {canReroute ? (
          <Button label="Reroute to another provider" variant="secondary" onPress={() => props.onPickProvider("reroute")} loading={busy} />
        ) : null}
        {s === "CLOSED" || s === "RESOLVED" ? <AppText tone="muted" size="sm">No admin actions available.</AppText> : null}
      </Card>
    );
  }

  // PROVIDER
  return (
    <Card style={{ gap: theme.space(2) }}>
      {s === "ASSIGNED" ? (
        <>
          <Button label="Accept job" onPress={props.onAccept} loading={busy} />
          <Field placeholder="Reason for declining (optional)" value={props.reason} onChangeText={props.setReason} />
          <Button label="Decline" variant="danger" onPress={props.onReject} loading={busy} />
        </>
      ) : null}
      {s === "ACCEPTED" ? <Button label="Start work" onPress={props.onStart} loading={busy} /> : null}
      {s === "IN_PROGRESS" ? (
        <>
          <Field placeholder="Resolution notes" value={props.notes} onChangeText={props.setNotes} />
          <Button label="Mark resolved" onPress={props.onProviderResolve} loading={busy} />
          <Field placeholder="Reason for hold" value={props.reason} onChangeText={props.setReason} />
          <Button label="Put on hold" variant="secondary" onPress={props.onHold} loading={busy} />
        </>
      ) : null}
      {s === "ON_HOLD" ? (
        <>
          <Button label="Resume work" onPress={props.onResume} loading={busy} />
          <Field placeholder="Resolution notes" value={props.notes} onChangeText={props.setNotes} />
          <Button label="Mark resolved" variant="secondary" onPress={props.onProviderResolve} loading={busy} />
        </>
      ) : null}
      {s === "REOPENED" ? (
        <>
          <Field placeholder="Resolution notes" value={props.notes} onChangeText={props.setNotes} />
          <Button label="Mark resolved" onPress={props.onProviderResolve} loading={busy} />
        </>
      ) : null}
      {["RESOLVED", "CLOSED", "REJECTED"].includes(s) ? (
        <AppText tone="muted" size="sm">
          Nothing to do right now.
        </AppText>
      ) : null}
    </Card>
  );
}

function ProviderPicker({ onPick, onCancel }: { onPick: (p: ProviderView) => void; onCancel: () => void }) {
  const { theme } = useTheme();
  const list = useAsync(() => admin.providers(), []);
  return (
    <Card style={{ gap: theme.space(2), borderColor: theme.color.primary }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText weight="700">Choose a provider</AppText>
        <AppText tone="primary" size="sm" onPress={onCancel}>
          Cancel
        </AppText>
      </View>
      {list.loading ? <Loading /> : null}
      {(list.data ?? []).map((p) => {
        const disabled = !p.assignable;
        return (
          <Pressable
            key={p.id}
            disabled={disabled}
            onPress={() => onPick(p)}
            style={{
              padding: theme.space(3),
              borderRadius: theme.radius.sm,
              borderWidth: 1,
              borderColor: theme.color.border,
              opacity: disabled ? 0.45 : 1,
            }}
          >
            <AppText weight="600">{p.name}</AppText>
            <AppText size="xs" tone="faint">
              {p.verificationStatus}
              {disabled ? " · not assignable" : ""}
            </AppText>
          </Pressable>
        );
      })}
    </Card>
  );
}
