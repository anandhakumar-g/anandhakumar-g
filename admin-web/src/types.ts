export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AnalyticsPoint {
  periodStart: string;
  ticketsCreated: number;
  ticketsResolved: number;
  offersRedeemed: number;
  newUsers: number;
  revenue: number;
}
export interface AnalyticsTotals {
  communities: number;
  activeCommunities: number;
  residents: number;
  providers: number;
  openTickets: number;
  mrr: number;
}
export interface AnalyticsView {
  bucket: "WEEK" | "MONTH";
  series: AnalyticsPoint[];
  totals: AnalyticsTotals;
}

export interface TenantHealth {
  id: string;
  name: string;
  city: string | null;
  openTickets: number;
  totalTickets: number;
}

export interface CommunityRequest {
  tenantId: string;
  name: string;
  city: string | null;
  locality: string | null;
  status: string;
  requestedAt: string;
  requestedByUserId: string | null;
  requestedByName: string | null;
  requestedByPhoneMasked: string | null;
}

export interface SuperProvider {
  id: string;
  name: string;
  vendorCategoryId: string;
  company: boolean;
  contactPhone: string | null;
  contactEmail: string | null;
  verificationStatus: string;
  tier: string;
  ratingAvg: number | null;
  ratingCount: number;
}

export interface KycDoc {
  id: string;
  type: string;
  status: string;
  originalFilename: string | null;
  uploadedAt: string;
  reviewedAt: string | null;
}

export interface OfferView {
  id: string;
  title: string;
  status: string;
  discountType: "FLAT" | "PERCENTAGE";
  discountValue: number;
  validFrom: string;
  validTo: string;
  target: { targetType: string; summary: string | null } | null;
  rejectReason: string | null;
}

export interface PlanView {
  id: string;
  target: "TENANT" | "PROVIDER";
  code: string;
  name: string;
  billingCycle: string;
  priceAmount: number;
  entitlements: Record<string, number>;
  isDefault: boolean;
}
export interface SubscriptionView {
  id: string;
  subjectType: string;
  subjectId: string;
  planId: string;
  status: string;
  currentPeriodEnd: string | null;
}
export interface InvoiceView {
  id: string;
  subscriptionId: string;
  amount: number;
  status: string;
  periodStart: string;
  periodEnd: string;
  paidAt: string | null;
}
