import React, { useEffect, useState } from "react";
import { Pressable, View } from "react-native";
import { catalog, communities, offers as offersApi } from "@/api/endpoints";
import { groupVendorCategories, OfferView, TenantCard, VendorCategory } from "@/api/types";
import { Button } from "./Button";
import { Divider, Pill, Segmented } from "./Bits";
import { Field } from "./Field";
import { AppText, Card } from "./Themed";
import { useTheme } from "@/theme/ThemeProvider";

type Audience = "SINGLE_TENANT" | "ENQUIRY_BASED" | "TENANT_LIST" | "USER_LIST";

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
  const [targetType, setTargetType] = useState<Audience>("SINGLE_TENANT");
  const [enquiryCategoryId, setEnquiryCategoryId] = useState<string | null>(null);
  const [perPerson, setPerPerson] = useState(String(existing?.redemptionLimitPerUser ?? 1));
  const [totalCap, setTotalCap] = useState(
    existing?.redemptionLimitTotal != null ? String(existing.redemptionLimitTotal) : ""
  );
  // TENANT_LIST
  const [communityQuery, setCommunityQuery] = useState("");
  const [communityResults, setCommunityResults] = useState<TenantCard[]>([]);
  const [tenantIds, setTenantIds] = useState<string[]>([]);
  // USER_LIST
  const [phonesText, setPhonesText] = useState("");

  useEffect(() => {
    catalog.vendorCategories().then(setCats).catch(() => {});
    catalog
      .categories()
      .then((cs) => setEnquiryCats(cs.filter((c) => c.requestType === "ENQUIRY").map((c) => ({ id: c.id, name: c.name }))))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (targetType !== "TENANT_LIST") return;
    const t = setTimeout(() => {
      communities.search(communityQuery.trim() || undefined)
        .then((p) => setCommunityResults(p.content))
        .catch(() => {});
    }, 250);
    return () => clearTimeout(t);
  }, [communityQuery, targetType]);

  function toggleTenant(id: string) {
    setTenantIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));
  }

  const phones = phonesText.split(/[\s,]+/).map((s) => s.trim()).filter(Boolean);

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
      redemptionLimitPerUser: Math.max(1, Number(perPerson) || 1),
      redemptionLimitTotal: totalCap.trim() ? Number(totalCap) : undefined,
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
      let target: any;
      if (targetType === "ENQUIRY_BASED") target = { targetType, enquiryCategoryId };
      else if (targetType === "TENANT_LIST") target = { targetType, tenantIds };
      else if (targetType === "USER_LIST") target = { targetType, phones };
      else target = { targetType: "SINGLE_TENANT", tenantIds: tenantId ? [tenantId] : [] };
      await offersApi.submit(id, target);
      onSaved();
    } catch (e: any) {
      setErr(e.message ?? "Could not submit");
    } finally {
      setBusy("");
    }
  }

  const valid = vendorCategoryId && title.trim() && Number(discountValue) > 0
    && (targetType !== "ENQUIRY_BASED" || enquiryCategoryId)
    && (targetType !== "TENANT_LIST" || tenantIds.length > 0)
    && (targetType !== "USER_LIST" || phones.length > 0);

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

      <View style={{ flexDirection: "row", gap: theme.space(2) }}>
        <View style={{ flex: 1 }}>
          <Field label="Per person" value={perPerson} onChangeText={setPerPerson} keyboardType="number-pad" />
        </View>
        <View style={{ flex: 1 }}>
          <Field
            label="Total (blank = ∞)"
            value={totalCap}
            onChangeText={setTotalCap}
            keyboardType="number-pad"
            placeholder="e.g. 100"
          />
        </View>
      </View>

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
        onChange={(v) => setTargetType(v as Audience)}
        options={[
          { value: "SINGLE_TENANT", label: "This community" },
          { value: "TENANT_LIST", label: "Communities" },
          { value: "USER_LIST", label: "Specific people" },
          ...(allowEnquiryTarget ? [{ value: "ENQUIRY_BASED", label: "Enquired" }] : []),
        ]}
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
      {targetType === "TENANT_LIST" ? (
        <View style={{ gap: theme.space(1.5) }}>
          {tenantIds.length > 0 ? (
            <AppText size="xs" tone="faint">{tenantIds.length} selected</AppText>
          ) : null}
          <Field placeholder="Search communities" value={communityQuery} onChangeText={setCommunityQuery} />
          {communityResults.map((t) => (
            <Pressable
              key={t.id}
              onPress={() => toggleTenant(t.id)}
              style={{
                flexDirection: "row", justifyContent: "space-between", alignItems: "center",
                padding: theme.space(2.5), borderRadius: theme.radius.sm, borderWidth: 1,
                borderColor: tenantIds.includes(t.id) ? theme.color.primary : theme.color.border,
              }}
            >
              <AppText size="sm">{t.name}</AppText>
              {tenantIds.includes(t.id) ? <Pill text="✓" tone="primary" /> : null}
            </Pressable>
          ))}
        </View>
      ) : null}
      {targetType === "USER_LIST" ? (
        <View style={{ gap: theme.space(1) }}>
          <Field
            label="Phone numbers"
            placeholder="+9198… , +9199…"
            value={phonesText}
            onChangeText={setPhonesText}
            multiline
            autoCapitalize="none"
          />
          <AppText size="xs" tone="faint">
            {phones.length} number{phones.length === 1 ? "" : "s"} · unregistered numbers are skipped.
          </AppText>
        </View>
      ) : null}

      <View style={{ flexDirection: "row", gap: theme.space(2) }}>
        <Button label="Save draft" variant="secondary" fullWidth={false} loading={busy === "save"} onPress={save} />
        <Button label="Submit for approval" fullWidth={false} loading={busy === "submit"} disabled={!valid} onPress={submit} />
      </View>
    </Card>
  );
}
