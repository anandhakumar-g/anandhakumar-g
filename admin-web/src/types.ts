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
  status: string;
  openTickets: number;
  totalTickets: number;
}

export interface VendorKind {
  id: string;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
}
export interface VendorCategoryRow {
  id: string;
  name: string;
  kind: string;
  parentCategoryId: string | null;
  sortOrder: number;
  active: boolean;
}
export interface TicketCategoryRow {
  id: string;
  tenantId: string | null;
  name: string;
  requestType: string;
  parentCategoryId: string | null;
  slaHours: number | null;
  defaultProviderKind: string | null;
  sortOrder: number;
  active: boolean;
}

export interface AuditLogRow {
  id: string;
  at: string;
  actorUserId: string | null;
  actorName: string | null;
  actorPhoneMasked: string | null;
  actorRole: string | null;
  tenantId: string | null;
  tenantName: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  httpMethod: string | null;
  endpoint: string | null;
  requestId: string | null;
  success: boolean;
  errorCode: string | null;
  durationMs: number | null;
  detail: string | null;
}

export interface BackupStatus {
  enabled: boolean;
  retentionDays: number;
  bucket?: string;
  lastObjectKey?: string;
  lastBackupAt?: string;
  ageHours?: number;
}

export interface BroadcastRow {
  id: string;
  scope: string;
  status: string;
  scheduledFor: string | null;
  tenantId: string | null;
  tenantName: string | null;
  senderUserId: string | null;
  senderName: string | null;
  senderRole: string | null;
  title: string;
  body: string;
  recipientCount: number;
  at: string;
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
  subjectType: string;
  subjectId: string;
  planId: string;
  status: string;
  currentPeriodEnd: string | null;
  gatewaySubscriptionId: string | null;
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
