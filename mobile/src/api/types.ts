export type OnboardingState = "NEEDS_PROFILE" | "NEEDS_COMMUNITY" | "PENDING_APPROVAL" | "READY";
export type Role = "RESIDENT" | "ADMIN" | "PROVIDER" | "SUPER_ADMIN";

export interface UserSummary {
  id: string;
  role: Role;
  name: string | null;
  phoneMasked: string;
  profileCompleted: boolean;
  preferredTheme: string | null;
  activeTenantId?: string | null;
}

export interface SessionResponse {
  token: string;
  expiresInSeconds: number;
  onboardingState: OnboardingState;
  user: UserSummary;
}

export interface TenantBranding {
  tenantId: string;
  name: string;
  logoUrl: string | null;
  defaultTheme: string | null;
  brandPrimaryColor: string | null;
}

export interface MembershipView {
  tenantId: string;
  tenantName: string | null;
  status: string;
  relation: string;
  flatId: string | null;
  flatLabel: string | null;
}

export interface MeResponse {
  userId: string;
  role: Role;
  name: string | null;
  phoneMasked: string;
  email: string | null;
  profileCompleted: boolean;
  preferredTheme: string | null;
  activeTenantId: string | null;
  activeTenantBranding: TenantBranding | null;
  memberships: MembershipView[];
  awayUntil: string | null;
}

export type Availability = "AVAILABLE" | "BUSY" | "AWAY";

export interface ProviderProfile {
  id: string;
  name: string;
  contactPhoneMasked: string;
  contactEmail: string | null;
  serviceArea: string | null;
  availability: Availability;
  availabilityNote: string | null;
  verificationStatus: string;
  tier: string;
}

export interface TenantCard {
  id: string;
  name: string;
  city: string | null;
  locality: string | null;
  logoUrl: string | null;
}

export interface Category {
  id: string;
  name: string;
  requestType: string;
  slaHours: number | null;
  sortOrder: number;
}

export interface VendorCategory {
  id: string;
  name: string;
  kind: string;
  kindLabel: string;
  parentCategoryId: string | null;
  sortOrder: number;
}

/** Group a flat vendor-category list by kind, preserving sort order. */
export function groupVendorCategories(list: VendorCategory[]): { kind: string; kindLabel: string; items: VendorCategory[] }[] {
  const order: string[] = [];
  const map = new Map<string, { kind: string; kindLabel: string; items: VendorCategory[] }>();
  for (const v of [...list].sort((a, b) => a.sortOrder - b.sortOrder)) {
    if (!map.has(v.kind)) {
      map.set(v.kind, { kind: v.kind, kindLabel: v.kindLabel, items: [] });
      order.push(v.kind);
    }
    map.get(v.kind)!.items.push(v);
  }
  return order.map((k) => map.get(k)!);
}

export interface PartyView {
  name: string | null;
  phone: string | null;
  verificationStatus: string | null;
  tier: string | null;
  ratingAvg: number | null;
  ratingCount: number | null;
  availability: string | null;
  awayUntil: string | null;
}

export interface TicketView {
  id: string;
  referenceCode: string;
  status: string;
  requestMode: string;
  categoryId: string;
  priority: string | null;
  description: string;
  flatLabel: string | null;
  serviceAddressText: string | null;
  serviceGeoLat: number | null;
  serviceGeoLng: number | null;
  serviceLandmark: string | null;
  preferredTimeWindow: string | null;
  raisedBy: PartyView | null;
  assignedProvider: PartyView | null;
  allocationApprovedByResident: boolean;
  rating: number | null;
  ratingComment: string | null;
  reopenedCount: number;
  resolutionNotes: string | null;
  holdReason: string | null;
  slaDueAt: string | null;
  slaBreachedAt: string | null;
  acknowledgedAt: string | null;
  assignedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TimelineEntry {
  fromStatus: string | null;
  toStatus: string;
  actorRole: string | null;
  actorName: string | null;
  remarks: string | null;
  at: string;
}

export interface TicketCategory {
  id: string;
  tenantId: string | null;
  name: string;
  requestType: "ISSUE" | "FEEDBACK" | "ENQUIRY";
  parentCategoryId: string | null;
  slaHours: number | null;
  defaultProviderKind: string | null;
  sortOrder: number;
  active: boolean;
}

export interface CommunitySettings {
  id: string;
  name: string;
  city: string | null;
  locality: string | null;
  defaultTheme: string | null;
  brandPrimaryColor: string | null;
  reopenWindowHours: number;
  requireAllocationApproval: boolean;
  categoryAdmin: "SUPER_ADMIN" | "COMMUNITY";
}

export interface AttachmentView {
  id: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  originalFilename: string | null;
  at: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProviderView {
  id: string;
  name: string;
  vendorCategoryId: string;
  company: boolean;
  contactPhoneMasked: string;
  verificationStatus: string;
  tier: string;
  active: boolean;
  assignable: boolean;
  ratingAvg: number | null;
  ratingCount: number;
  availability: Availability;
  availabilityNote: string | null;
}

export interface JoinRequestView {
  id: string;
  userId: string;
  userName: string | null;
  userPhoneMasked: string | null;
  requestedFlatLabel: string | null;
  status: string;
  createdAt: string;
}

export interface FlatView {
  id: string;
  block: string | null;
  flatNumber: string;
  label: string;
  occupancyType: string;
  addressText: string | null;
  geoLat: number | null;
  geoLng: number | null;
  currentOccupantUserId: string | null;
  ownerUserId: string | null;
}

export interface InviteView {
  id: string;
  code: string;
  relation: string;
  status: string;
  flatId: string | null;
  maxUses: number;
  useCount: number;
  expiresAt: string | null;
  createdAt: string;
}

// ---- MVP-2: KYC, offers, notification preferences ------------------------

export type KycDocType = "GOV_ID" | "ADDRESS_PROOF" | "COMPANY_REG" | "OTHER";
export interface KycDocView {
  id: string;
  docType: KycDocType;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  contentType: string;
  sizeBytes: number;
  originalFilename: string | null;
  reviewNote: string | null;
  reviewedAt: string | null;
  uploadedAt: string;
}

export interface NotificationPreferences {
  subscribedVendorCategoryIds: string[];
  promoFrequencyCapPerWeek: number;
  digestMode: "OFF" | "DAILY" | "WEEKLY";
  ticketNotificationsEnabled: boolean;
  promoNotificationsEnabled: boolean;
  whatsappEnabled: boolean;
}

export type OfferStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "EXPIRED" | "CANCELLED" | "REJECTED";
export type TargetType = "SINGLE_TENANT" | "TENANT_LIST" | "ALL_TENANTS" | "USER_SEGMENT" | "ENQUIRY_BASED";

export interface OfferTargetView {
  targetType: TargetType;
  tenantIds: string[];
  enquiryCategoryId: string | null;
  segmentFilter: string | null;
  summary: string | null;
}

export interface OfferView {
  id: string;
  title: string;
  description: string | null;
  status: OfferStatus;
  vendorCategoryId: string;
  discountType: "FLAT" | "PERCENTAGE";
  discountValue: number;
  couponCode: string | null;
  imageUrl: string | null;
  validFrom: string;
  validTo: string;
  redemptionLimitPerUser: number;
  redemptionLimitTotal: number | null;
  terms: string | null;
  createdByRole: string;
  rejectReason: string | null;
  target: OfferTargetView | null;
  submittedAt: string | null;
  validatedAt: string | null;
  createdAt: string;
}

export interface RedemptionView {
  id: string;
  offerId: string;
  status: string;
  verifiedBy: string;
  redeemedAt: string;
  confirmedAt: string | null;
  couponCode: string | null;
}

export function discountLabel(o: Pick<OfferView, "discountType" | "discountValue">): string {
  const v = Number(o.discountValue);
  return o.discountType === "PERCENTAGE" ? `${v}% off` : `₹${v} off`;
}

// ---- MVP-3: payments + taxonomy ---------------------------------------

export type PaymentStatus =
  | "PENDING" | "CASH_PENDING_OTP" | "PAID_ONLINE" | "PAID_CASH" | "WAIVED" | "FAILED";

export interface PaymentView {
  id: string;
  amount: number;
  currency: string;
  mode: "CASH" | "ONLINE" | null;
  status: PaymentStatus;
  note: string | null;
  payLink: string | null;
  receiptNumber: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface ReceiptView {
  receiptNumber: string;
  amount: number;
  currency: string;
  mode: string;
  ticketReference: string;
  payerName: string | null;
  payeeName: string | null;
  issuedAt: string;
  shareText: string;
}

export interface VendorCategoryKind {
  id: string;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
}

export interface AdminVendorCategory {
  id: string;
  name: string;
  kind: string;
  parentCategoryId: string | null;
  sortOrder: number;
  active: boolean;
}

export function money(amount: number, currency = "INR"): string {
  const sym = currency === "INR" ? "₹" : currency + " ";
  return sym + Number(amount).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

// ---- MVP-4: billing / subscriptions ---------------------------------

export type SubjectType = "TENANT" | "PROVIDER";
export type SubscriptionStatus =
  | "TRIAL" | "ACTIVE" | "PAST_DUE" | "EXPIRED" | "CANCELLED" | "COMPED";
export type InvoiceStatus = "DUE" | "PAID" | "VOID";
export type ProviderTier = "STANDARD" | "FEATURED";

export interface PlanView {
  id: string;
  target: SubjectType;
  code: string;
  name: string;
  description: string | null;
  billingCycle: string;
  price: number;
  currency: string;
  entitlements: Record<string, number>;
  active: boolean;
  isDefault: boolean;
  sortOrder: number;
}

export interface SubscriptionView {
  id: string;
  subjectType: SubjectType;
  subjectId: string;
  planId: string;
  planCode: string | null;
  planName: string | null;
  status: SubscriptionStatus;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  graceUntil: string | null;
  autoRenew: boolean;
}

export interface InvoiceView {
  id: string;
  subscriptionId: string;
  subjectType: SubjectType;
  subjectId: string;
  amount: number;
  currency: string;
  periodStart: string;
  periodEnd: string;
  status: InvoiceStatus;
  paymentLink: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface UsageView {
  feature: string;
  limit: number; // -1 unlimited, 0 disabled, positive cap
  used: number;
}

export interface MyBillingView {
  subjectType: SubjectType;
  subjectId: string;
  providerTier: ProviderTier | null;
  plan: PlanView;
  subscription: SubscriptionView | null;
  usage: UsageView[];
  dueInvoices: InvoiceView[];
  upgradeOptions: PlanView[];
}

export function featureLabel(feature: string): string {
  const s = feature.toLowerCase().replace(/_/g, " ");
  return s.replace(/ per month$/, " / month");
}

export function subscriptionTone(
  status: SubscriptionStatus
): "success" | "primary" | "danger" | "muted" {
  if (status === "ACTIVE" || status === "COMPED" || status === "TRIAL") return "success";
  if (status === "PAST_DUE") return "primary";
  if (status === "EXPIRED") return "danger";
  return "muted";
}
