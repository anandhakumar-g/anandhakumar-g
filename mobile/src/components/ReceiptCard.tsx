import React from "react";
import { Share, View } from "react-native";
import { ReceiptView } from "@/api/types";
import { useTheme } from "@/theme/ThemeProvider";
import { KeyValue } from "./Bits";
import { Button } from "./Button";
import { AppText, Card } from "./Themed";

export function ReceiptCard({ receipt }: { receipt: ReceiptView }) {
  const { theme } = useTheme();
  return (
    <Card style={{ gap: theme.space(2), borderColor: theme.color.success }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
        <AppText size="sm" weight="700" tone="success">
          Payment received
        </AppText>
        <AppText size="xs" tone="faint">
          {new Date(receipt.issuedAt).toLocaleDateString()}
        </AppText>
      </View>
      <AppText size="xxl" weight="700" family="display">
        {receipt.currency === "INR" ? "₹" : receipt.currency + " "}
        {Number(receipt.amount).toLocaleString()}
      </AppText>
      <View style={{ gap: theme.space(0.5) }}>
        <KeyValue k="Receipt" v={receipt.receiptNumber} />
        <KeyValue k="Ticket" v={receipt.ticketReference} />
        <KeyValue k="Mode" v={receipt.mode === "CASH" ? "Cash (OTP confirmed)" : "Online"} />
        <KeyValue k="Paid by" v={receipt.payerName} />
        <KeyValue k="Paid to" v={receipt.payeeName} />
      </View>
      <Button
        label="Share receipt"
        variant="secondary"
        onPress={() => Share.share({ message: receipt.shareText }).catch(() => {})}
      />
    </Card>
  );
}
