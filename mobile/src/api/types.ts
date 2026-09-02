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
  serviceAddressText: string;
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
  remarks: string | null;
  at: string;
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
