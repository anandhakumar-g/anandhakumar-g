import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { catalog, offers as offersApi } from "@/api/endpoints";
import { groupVendorCategories, OfferView, VendorCategory } from "@/api/types";
import { Button } from "./Button";
import { Divider, Segmented } from "./Bits";
import { Field } from "./Field";
import { AppText, Card } from "./Themed";
import { useTheme } from "@/theme/ThemeProvider";

const DAY = 86400000;

export function OfferForm({
  existing,
  onSaved,
  tenantId,
  allowEnquiryTarget = true,
}: {
  existing?: OfferView | null;
  onSaved: () => void;
  /** Author's community — used for the SINGLE_TENANT suggested audience. */
  tenantId?: string | null;
  allowEnquiryTarget?: boolean;
}) {
  const { theme } = useTheme();
  const [cats, setCats] = useState<VendorCategory[]>([]);
  const [enquiryCats, setEnquiryCats] = useState<{ id: string; name: string }[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState<"" | "save" | "submit">("");

  const [vendorCategoryId, setVcat] = useState(existing?.vendorCategoryId ?? "");
  const [title, setTitle] = useState(existing?.title ?? "");
  const [description, setDescription] = useState(existing?.description ?? "");
  const [discountType, setDiscountType] = useState<"PERCENTAGE" | "FLAT">(existing?.discountType ?? "PERCENTAGE");
  const [discountValue, setDiscountValue] = useState(String(existing?.discountValue ?? ""));
  const [couponCode, setCouponCode] = useState(existing?.couponCode ?? "");
  const [days, setDays] = useState(30);
  const [targetType, setTargetType] = useState<"SINGLE_TENANT" | "ENQUIRY_BASED">("SINGLE_TENANT");
  const [enquiryCategoryId, setEnquiryCategoryId] = useState<string | null>(null);

  useEffect(() => {
    catalog.vendorCategories().then(setCats).catch(() => {});
    catalog
      .categories()
      .then((cs) => setEnquiryCats(cs.filter((c) => c.requestType === "ENQUIRY").map((c) => ({ id: c.id, name: c.name }))))
      .catch(() => {});
  }, []);

  function body() {
    const now = Date.now();
    return {
      vendorCategoryId,
      title: title.trim(),
      description: description.trim() || undefined,
      discountType,
      discountValue: Number(discountValue),
      couponCode: couponCode.trim() || undefined,
      validFrom: new Date(now - 60000).toISOString(),
      validTo: new Date(now + days * DAY).toISOString(),
      redemptionLimitPerUser: 1,
    };
  }

  async function persist(): Promise<string> {
    if (existing) {
      await offersApi.update(existing.id, body());
      return existing.id;
    }
    const created = await offersApi.create(body());
    return created.id;
  }

  async function save() {
    setErr(null);
    setBusy("save");
    try {
      await persist();
      onSaved();
    } catch (e: any) {
      setErr(e.message ?? "Could not save");
    } finally {
      setBusy("");
    }
  }

  async function submit() {
    setErr(null);
    setBusy("submit");
    try {
      const id = await persist();
      const target =
        targetType === "ENQUIRY_BASED"
          ? { targetType: "ENQUIRY_BASED", enquiryCategoryId }
          : { targetType: "SINGLE_TENANT", tenantIds: tenantId ? [tenantId] : [] };
      await offersApi.submit(id, target);
      onSaved();
    } catch (e: any) {
      setErr(e.message ?? "Could not submit");
    } finally {
      setBusy("");
    }
  }

  const valid = vendorCategoryId && title.trim() && Number(discountValue) > 0
    && (targetType !== "ENQUIRY_BASED" || enquiryCategoryId);

  return (
    <Card style={{ gap: theme.space(3) }}>
      <AppText size="lg" weight="700">
        {existing ? "Edit offer" : "New offer"}
      </AppText>
      {err ? (
        <AppText tone="danger" size="sm">
          {err}
        </AppText>
      ) : null}

      <Field label="Title" value={title} onChangeText={setTitle} placeholder="Festive 20% off sweets" />
      <Field label="Description" value={description} onChangeText={setDescription} multiline />

      <AppText size="sm" weight="600" tone="muted">
        Category
      </AppText>
      {groupVendorCategories(cats).map((g) => (
        <View key={g.kind} style={{ gap: theme.space(1) }}>
          <AppText size="xs" weight="700" tone="faint">
            {g.kindLabel.toUpperCase()}
          </AppText>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
            {g.items.map((c) => (
              <Button
                key={c.id}
                label={c.name}
                fullWidth={false}
                variant={vendorCategoryId === c.id ? "primary" : "secondary"}
                onPress={() => setVcat(c.id)}
              />
            ))}
          </View>
        </View>
      ))}

      <Segmented
        value={discountType}
        onChange={(v) => setDiscountType(v as any)}
        options={[
          { value: "PERCENTAGE", label: "% off" },
          { value: "FLAT", label: "₹ off" },
        ]}
      />
      <Field
        label={discountType === "PERCENTAGE" ? "Percent off" : "Rupees off"}
        value={discountValue}
        onChangeText={setDiscountValue}
        keyboardType="numeric"
      />
      <Field label="Coupon code" value={couponCode} onChangeText={setCouponCode} autoCapitalize="characters" placeholder="DIWALI20" />

      <View style={{ gap: theme.space(1.5) }}>
        <AppText size="sm" weight="600" tone="muted">
          Runs for
        </AppText>
        <Segmented
          value={String(days)}
          onChange={(v) => setDays(Number(v))}
          options={[
            { value: "7", label: "1 week" },
            { value: "14", label: "2 weeks" },
            { value: "30", label: "1 month" },
          ]}
        />
      </View>

      <Divider />
      <AppText size="sm" weight="600" tone="muted">
        Suggested audience (Super Admin confirms this)
      </AppText>
      <Segmented
        value={targetType}
        onChange={(v) => setTargetType(v as any)}
        options={
          allowEnquiryTarget
            ? [
                { value: "SINGLE_TENANT", label: "This community" },
                { value: "ENQUIRY_BASED", label: "Enquired before" },
              ]
            : [{ value: "SINGLE_TENANT", label: "This community" }]
        }
      />
      {targetType === "ENQUIRY_BASED" ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: theme.space(2) }}>
          {enquiryCats.map((c) => (
            <Button
              key={c.id}
              label={c.name}
              fullWidth={false}
              variant={enquiryCategoryId === c.id ? "primary" : "secondary"}
              onPress={() => setEnquiryCategoryId(c.id)}
            />
          ))}
        </View>
      ) : null}

      <View style={{ flexDirection: "row", gap: theme.space(2) }}>
        <Button label="Save draft" variant="secondary" fullWidth={false} loading={busy === "save"} onPress={save} />
        <Button label="Submit for approval" fullWidth={false} loading={busy === "submit"} disabled={!valid} onPress={submit} />
      </View>
    </Card>
  );
}
