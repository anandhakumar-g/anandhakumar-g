import * as WebBrowser from "expo-web-browser";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { payments } from "@/api/endpoints";
import { money, PaymentView, ReceiptView, Role } from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";
import { Pill, Segmented } from "./Bits";
import { Button } from "./Button";
import { Field } from "./Field";
import { ReceiptCard } from "./ReceiptCard";
import { AppText, Card } from "./Themed";

export function PaymentPanel({
  ticketId,
  role,
  ticketStatus,
}: {
  ticketId: string;
  role: Role;
  ticketStatus: string;
}) {
  const { theme } = useTheme();
  const [payment, setPayment] = useState<PaymentView | null | undefined>(undefined);
  const [receipt, setReceipt] = useState<ReceiptView | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [otp, setOtp] = useState("");
  const [devOtp, setDevOtp] = useState<string | null>(null);
  const pollRef = useRef<any>(null);

  const load = useCallback(async () => {
    try {
      const p = await payments.forTicket(ticketId);
      setPayment(p);
      if (p && (p.status === "PAID_CASH" || p.status === "PAID_ONLINE")) {
        setReceipt(await payments.receipt(ticketId).catch(() => null));
      }
    } catch {
      setPayment(null);
    }
  }, [ticketId]);

  useEffect(() => {
    load();
    return () => clearInterval(pollRef.current);
  }, [load]);

  // poll while an online payment is in flight
  useEffect(() => {
    clearInterval(pollRef.current);
    if (payment?.status === "PENDING" && payment.mode === "ONLINE") {
      pollRef.current = setInterval(load, 3000);
    }
    return () => clearInterval(pollRef.current);
  }, [payment?.status, payment?.mode, load]);

  async function run(fn: () => Promise<any>) {
    setBusy(true);
    setErr(null);
    try {
      await fn();
      await load();
    } catch (e: any) {
      setErr(e.message ?? "Payment action failed");
    } finally {
      setBusy(false);
    }
  }

  if (payment === undefined) return null;

  const canCharge = (role === "ADMIN" || role === "PROVIDER")
    && (ticketStatus === "RESOLVED" || ticketStatus === "CLOSED");

  // ---- no charge yet ----
  if (!payment) {
    if (!canCharge) return null;
    return (
      <Card style={{ gap: theme.space(2) }}>
        <AppText size="sm" weight="700" tone="muted">
          Charge for this job
        </AppText>
        <Field label="Amount (₹)" value={amount} onChangeText={setAmount} keyboardType="numeric" placeholder="500" />
        <Field label="Note (optional)" value={note} onChangeText={setNote} placeholder="Parts + labour" />
        <Button
          label="Add charge"
          loading={busy}
          disabled={!(Number(amount) > 0)}
          onPress={() => run(() => payments.charge(ticketId, Number(amount), note.trim() || undefined))}
        />
        {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}
      </Card>
    );
  }

  const p = payment;

  return (
    <Card style={{ gap: theme.space(2.5) }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="sm" weight="700" tone="muted">
          Payment
        </AppText>
        <Pill
          text={p.status.replace(/_/g, " ")}
          tone={p.status.startsWith("PAID") ? "success" : p.status === "WAIVED" ? "muted" : "primary"}
        />
      </View>
      <AppText size="xl" weight="700" family="display">
        {money(p.amount, p.currency)}
      </AppText>
      {p.note ? <AppText size="sm" tone="muted">{p.note}</AppText> : null}

      {/* resident */}
      {role === "RESIDENT" && p.status === "PENDING" && !p.mode ? (
        <View style={{ gap: theme.space(2) }}>
          <AppText size="sm" tone="muted">Choose how you'd like to pay.</AppText>
          <View style={{ flexDirection: "row", gap: theme.space(2) }}>
            <Button label="Pay online" fullWidth={false} loading={busy}
              onPress={() => run(() => payments.chooseMode(ticketId, "ONLINE"))} />
            <Button label="Pay cash" variant="secondary" fullWidth={false} loading={busy}
              onPress={() => run(() => payments.chooseMode(ticketId, "CASH"))} />
          </View>
        </View>
      ) : null}

      {role === "RESIDENT" && p.status === "PENDING" && p.mode === "ONLINE" ? (
        <View style={{ gap: theme.space(2) }}>
          <Button label="Pay now" loading={busy}
            onPress={async () => { if (p.payLink) await WebBrowser.openBrowserAsync(p.payLink); load(); }} />
          <Button label="I've paid — refresh" variant="ghost" onPress={load} />
        </View>
      ) : null}

      {role === "RESIDENT" && p.status === "PENDING" && p.mode === "CASH" ? (
        <AppText size="sm" tone="muted">
          Pay the technician in cash. They'll start the confirmation and a code will be sent to your phone.
        </AppText>
      ) : null}

      {/* provider */}
      {role === "PROVIDER" && p.status === "PENDING" && p.mode === "CASH" ? (
        <Button label="I've collected the cash" loading={busy}
          onPress={() => run(async () => {
            const r = await payments.collectCash(ticketId);
            if (r.devOtp) setDevOtp(r.devOtp);
          })} />
      ) : null}
      {role === "PROVIDER" && p.status === "PENDING" && !p.mode ? (
        <AppText size="sm" tone="muted">Waiting for the resident to choose a payment method.</AppText>
      ) : null}

      {/* cash OTP — either party can confirm */}
      {p.status === "CASH_PENDING_OTP" && (role === "RESIDENT" || role === "PROVIDER") ? (
        <View style={{ gap: theme.space(2) }}>
          <AppText size="sm" tone="muted">
            {role === "RESIDENT"
              ? "Enter the code sent to your phone, or read it out to the technician."
              : "Ask the resident for the code sent to their phone."}
          </AppText>
          {devOtp ? (
            <AppText size="sm" tone="primary" weight="700">Dev code: {devOtp}</AppText>
          ) : null}
          <Field label="Confirmation code" value={otp} onChangeText={setOtp} keyboardType="number-pad" />
          <Button label="Confirm payment" loading={busy} disabled={otp.trim().length < 4}
            onPress={() => run(() => payments.confirmCash(ticketId, otp.trim()))} />
        </View>
      ) : null}

      {/* admin */}
      {role === "ADMIN" && !p.status.startsWith("PAID") && p.status !== "WAIVED" ? (
        <View style={{ gap: theme.space(2) }}>
          <Field label="Adjust amount (₹)" value={amount} onChangeText={setAmount} keyboardType="numeric" placeholder={String(p.amount)} />
          <View style={{ flexDirection: "row", gap: theme.space(2) }}>
            <Button label="Update" variant="secondary" fullWidth={false} loading={busy} disabled={!(Number(amount) > 0)}
              onPress={() => run(() => payments.adjust(ticketId, Number(amount), note.trim() || undefined))} />
            <Button label="Waive" variant="danger" fullWidth={false} loading={busy}
              onPress={() => run(() => payments.waive(ticketId, note.trim() || "Waived by admin"))} />
          </View>
        </View>
      ) : null}

      {p.status === "WAIVED" ? (
        <AppText size="sm" tone="muted">This charge was waived.</AppText>
      ) : null}

      {receipt ? <ReceiptCard receipt={receipt} /> : null}
      {err ? <AppText tone="danger" size="sm">{err}</AppText> : null}
    </Card>
  );
}
