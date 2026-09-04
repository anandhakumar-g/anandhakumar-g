import { api, uploadFile } from "./client";
import {
  AdminAssignment, AdminVendorCategory, AttachmentView, Category, CommunitySettings, FlatView, InvoiceView,
  InviteView, JoinRequestView, KycDocView, LocationView, MeResponse, MemberView, MyBillingView,
  NotificationPreferences, OfferFeedbackList, OfferFeedbackView, OfferStatus, OfferView, HouseholdMember,
  MyFlat, Page, PaymentView, PlanView,
  ProviderProfile, ProviderTier, ProviderView, PublicProviderView, ReceiptView, RedemptionView, RemovalCheck,
  SessionResponse, SubscriptionStatus, SubscriptionView, SubjectType, SuperProviderView, TenantCard,
  TicketCategory, TicketView, TimelineEntry, VendorCategory, VendorCategoryKind,
} from "./types";

type TicketCategoryBody = Partial<{
  name: string; requestType: string; parentCategoryId: string;
  slaHours: number; defaultProviderKind: string; sortOrder: number;
}>;

export const auth = {
  requestOtp: (phone: string) =>
    api.post<{ sent: boolean; devCode: string | null }>("/auth/otp/request", { phone }, { auth: false }),
  verifyOtp: (phone: string, code: string) =>
    api.post<SessionResponse>("/auth/otp/verify", { phone, code }, { auth: false }),
  completeProfile: (name: string, email?: string) =>
    api.post<SessionResponse>("/auth/profile", { name, email }),
  refresh: () => api.post<SessionResponse>("/auth/refresh", {}),
};

export const me = {
  get: () => api.get<MeResponse>("/me"),
  setTheme: (theme: string) => api.put<void>("/me/theme", { theme }),
  registerDevice: (token: string, platform: string, provider = "expo") =>
    api.post<void>("/me/devices", { token, platform, provider }),
  setAwayUntil: (awayUntil: string | null) => api.put<void>("/me/away-until", { awayUntil }),
  notificationPreferences: () => api.get<NotificationPreferences>("/me/notification-preferences"),
  updateNotificationPreferences: (body: Partial<NotificationPreferences>) =>
    api.put<NotificationPreferences>("/me/notification-preferences", body),
  switchCommunity: (tenantId: string) => api.post<SessionResponse>("/me/active-community", { tenantId }),
  stopActing: () => api.post<SessionResponse>("/me/stop-acting", {}),
  leaveCommunity: (tenantId: string) => api.post<SessionResponse>(`/me/memberships/${tenantId}/leave`, {}),
  flats: () => api.get<MyFlat[]>("/me/flats"),
  householdMembers: (flatId: string) => api.get<HouseholdMember[]>(`/me/household/${flatId}/members`),
  createHouseholdInvite: (flatId: string, maxUses?: number, validDays?: number) =>
    api.post<{ code: string; maxUses: number; expiresAt: string }>("/me/household/invites",
      { flatId, maxUses, validDays }),
  removeHouseholdMember: (flatId: string, userId: string) =>
    api.post<HouseholdMember[]>(`/me/household/${flatId}/members/${userId}/remove`, {}),
};

export const providers = {
  list: () => api.get<PublicProviderView[]>("/providers"),
};

export const offers = {
  feed: () => api.get<OfferView[]>("/offers"),
  mine: () => api.get<OfferView[]>("/offers/mine"),
  get: (id: string) => api.get<OfferView>(`/offers/${id}`),
  create: (body: any) => api.post<OfferView>("/offers", body),
  update: (id: string, body: any) => api.put<OfferView>(`/offers/${id}`, body),
  submit: (id: string, target: any) => api.post<OfferView>(`/offers/${id}/submit`, { target }),
  uploadImage: (id: string, file: { uri: string; name: string; type: string }) =>
    uploadFile<OfferView>(`/offers/${id}/image`, file),
  redeem: (id: string, code?: string) => api.post<RedemptionView>(`/offers/${id}/redeem`, { code }),
  confirmRedemption: (rid: string) => api.post<RedemptionView>(`/offers/redemptions/${rid}/confirm`),
  leaveFeedback: (id: string, rating: number, comment?: string) =>
    api.post<OfferFeedbackView>(`/offers/${id}/feedback`, { rating, comment }),
  feedback: (id: string) => api.get<OfferFeedbackList>(`/offers/${id}/feedback`),
};

export const superOffers = {
  list: (status?: OfferStatus) =>
    api.get<OfferView[]>("/superadmin/offers", { query: { status } }),
  get: (id: string) => api.get<OfferView>(`/superadmin/offers/${id}`),
  approve: (id: string, target?: any) => api.post<OfferView>(`/superadmin/offers/${id}/approve`, target ? { target } : {}),
  reject: (id: string, reason: string) => api.post<OfferView>(`/superadmin/offers/${id}/reject`, { reason }),
};

export const kyc = {
  mine: () => api.get<KycDocView[]>("/provider/kyc"),
  upload: (docType: string, file: { uri: string; name: string; type: string }) =>
    uploadFile<KycDocView>("/provider/kyc", file, { docType }),
  // MVP-7: KYC review is Super-Admin only.
  forProvider: (providerId: string) => api.get<KycDocView[]>(`/superadmin/providers/${providerId}/kyc`),
  review: (providerId: string, docId: string, status: string, note?: string) =>
    api.post<KycDocView>(`/superadmin/providers/${providerId}/kyc/${docId}/review`, { status, note }),
};

export const payments = {
  forTicket: (ticketId: string) =>
    api.get<PaymentView | null>(`/tickets/${ticketId}/payment`),
  charge: (ticketId: string, amount: number, note?: string) =>
    api.post<PaymentView>(`/tickets/${ticketId}/payment/charge`, { amount, note }),
  adjust: (ticketId: string, amount: number, note?: string) =>
    api.put<PaymentView>(`/tickets/${ticketId}/payment/charge`, { amount, note }),
  chooseMode: (ticketId: string, mode: "CASH" | "ONLINE") =>
    api.post<PaymentView>(`/tickets/${ticketId}/payment/mode`, { mode }),
  collectCash: (ticketId: string) =>
    api.post<{ status: string; devOtp: string | null }>(`/tickets/${ticketId}/payment/cash/collect`),
  confirmCash: (ticketId: string, otp: string) =>
    api.post<PaymentView>(`/tickets/${ticketId}/payment/cash/confirm`, { otp }),
  waive: (ticketId: string, reason: string) =>
    api.post<PaymentView>(`/tickets/${ticketId}/payment/waive`, { reason }),
  receipt: (ticketId: string) =>
    api.get<ReceiptView>(`/tickets/${ticketId}/payment/receipt`),
};

export const taxonomy = {
  kinds: () => api.get<VendorCategoryKind[]>("/superadmin/vendor-category-kinds"),
  createKind: (code: string, label: string, sortOrder?: number) =>
    api.post<VendorCategoryKind>("/superadmin/vendor-category-kinds", { code, label, sortOrder }),
  renameKind: (id: string, label: string, sortOrder?: number) =>
    api.put<VendorCategoryKind>(`/superadmin/vendor-category-kinds/${id}`, { label, sortOrder }),
  deactivateKind: (id: string) =>
    api.post<VendorCategoryKind>(`/superadmin/vendor-category-kinds/${id}/deactivate`),
  categories: () => api.get<AdminVendorCategory[]>("/superadmin/vendor-categories"),
  createCategory: (name: string, kind: string, parentCategoryId?: string, sortOrder?: number) =>
    api.post<AdminVendorCategory>("/superadmin/vendor-categories", { name, kind, parentCategoryId, sortOrder }),
  renameCategory: (id: string, name: string, sortOrder?: number) =>
    api.put<AdminVendorCategory>(`/superadmin/vendor-categories/${id}`, { name, sortOrder }),
  deactivateCategory: (id: string) =>
    api.post<AdminVendorCategory>(`/superadmin/vendor-categories/${id}/deactivate`),
  reactivateCategory: (id: string) =>
    api.post<AdminVendorCategory>(`/superadmin/vendor-categories/${id}/reactivate`),
};

export const billing = {
  mine: () => api.get<MyBillingView>("/me/billing"),
  selfUpgrade: (planCode: string) => api.post<SubscriptionView>("/me/billing/plan", { planCode }),
  payInvoice: (invoiceId: string) =>
    api.post<{ paymentLink: string }>(`/me/billing/invoices/${invoiceId}/pay`, {}),
};

export const superBilling = {
  plans: (activeOnly = false) =>
    api.get<PlanView[]>("/superadmin/plans", { query: { activeOnly } }),
  createPlan: (body: {
    target: SubjectType; code: string; name: string; description?: string;
    billingCycle?: string; price?: number; entitlements?: Record<string, number>; sortOrder?: number;
  }) => api.post<PlanView>("/superadmin/plans", body),
  updatePlan: (id: string, body: {
    name?: string; description?: string; price?: number;
    entitlements?: Record<string, number>; active?: boolean; sortOrder?: number;
  }) => api.put<PlanView>(`/superadmin/plans/${id}`, body),
  subscriptions: (subjectType?: SubjectType, status?: SubscriptionStatus) =>
    api.get<SubscriptionView[]>("/superadmin/subscriptions", { query: { subjectType, status } }),
  assign: (body: { subjectType: SubjectType; subjectId: string; planCode: string; comp?: boolean }) =>
    api.post<SubscriptionView>("/superadmin/subscriptions", body),
  cancel: (id: string) => api.post<SubscriptionView>(`/superadmin/subscriptions/${id}/cancel`, {}),
  comp: (id: string) => api.post<SubscriptionView>(`/superadmin/subscriptions/${id}/comp`, {}),
  invoices: (status?: InvoiceView["status"]) =>
    api.get<InvoiceView[]>("/superadmin/invoices", { query: { status } }),
  markPaid: (id: string) => api.post<InvoiceView>(`/superadmin/invoices/${id}/mark-paid`, {}),
  providers: () => api.get<SuperProviderView[]>("/superadmin/providers"),
  setTier: (providerId: string, tier: ProviderTier) =>
    api.post<void>(`/superadmin/providers/${providerId}/tier`, { tier }),
};

/** MVP-7: the Super Admin owns provider onboarding, verification and KYC review. */
export const superProviders = {
  list: () => api.get<SuperProviderView[]>("/superadmin/providers"),
  create: (body: {
    name: string; vendorCategoryId: string; company: boolean; contactPhone: string;
    contactEmail?: string; serviceArea?: string;
  }) => api.post<SuperProviderView>("/superadmin/providers", body),
  update: (id: string, body: Partial<{
    name: string; vendorCategoryId: string; contactPhone: string; contactEmail: string; serviceArea: string;
  }>) => api.put<SuperProviderView>(`/superadmin/providers/${id}`, body),
  verify: (id: string, status: string) =>
    api.post<SuperProviderView>(`/superadmin/providers/${id}/verify`, { status }),
  setTier: (id: string, tier: ProviderTier) =>
    api.post<void>(`/superadmin/providers/${id}/tier`, { tier }),
};

export const communities = {
  search: (query?: string) => api.get<Page<TenantCard>>("/tenants", { query: { query, size: 30 } }),
  join: (tenantId: string, inviteCode?: string, requestedFlatLabel?: string) =>
    api.post<SessionResponse>("/memberships/join", { tenantId, inviteCode, requestedFlatLabel }),
};

export const catalog = {
  categories: () => api.get<Category[]>("/categories"),
  vendorCategories: () => api.get<VendorCategory[]>("/vendor-categories"),
};

export const superCategories = {
  list: (tenantId?: string) =>
    api.get<TicketCategory[]>("/superadmin/ticket-categories", { query: { tenantId } }),
  create: (body: TicketCategoryBody, tenantId?: string) =>
    api.post<TicketCategory>("/superadmin/ticket-categories", body, { query: { tenantId } }),
  update: (id: string, body: TicketCategoryBody, tenantId?: string) =>
    api.put<TicketCategory>(`/superadmin/ticket-categories/${id}`, body, { query: { tenantId } }),
  deactivate: (id: string, tenantId?: string) =>
    api.post<TicketCategory>(`/superadmin/ticket-categories/${id}/deactivate`, {}, { query: { tenantId } }),
  reactivate: (id: string, tenantId?: string) =>
    api.post<TicketCategory>(`/superadmin/ticket-categories/${id}/reactivate`, {}, { query: { tenantId } }),
};

export const adminCategories = {
  list: () => api.get<TicketCategory[]>("/admin/ticket-categories"),
  create: (body: TicketCategoryBody) => api.post<TicketCategory>("/admin/ticket-categories", body),
  update: (id: string, body: TicketCategoryBody) => api.put<TicketCategory>(`/admin/ticket-categories/${id}`, body),
  deactivate: (id: string) => api.post<TicketCategory>(`/admin/ticket-categories/${id}/deactivate`, {}),
  reactivate: (id: string) => api.post<TicketCategory>(`/admin/ticket-categories/${id}/reactivate`, {}),
};

export const tickets = {
  list: (status?: string, page = 0) =>
    api.get<Page<TicketView>>("/tickets", { query: { status, page, size: 50 } }),
  get: (id: string) => api.get<TicketView>(`/tickets/${id}`),
  timeline: (id: string) => api.get<TimelineEntry[]>(`/tickets/${id}/timeline`),
  attachments: (id: string) => api.get<AttachmentView[]>(`/tickets/${id}/attachments`),
  raise: (body: {
    categoryId: string; description: string; priority?: string;
    serviceAddressText?: string; serviceGeoLat?: number; serviceGeoLng?: number;
    serviceLandmark?: string; preferredTimeWindow?: string; flatId?: string; providerId?: string;
  }) => api.post<TicketView>("/tickets", body),
  upload: (id: string, file: { uri: string; name: string; type: string }) =>
    uploadFile<AttachmentView>(`/tickets/${id}/attachments`, file),
  // admin
  acknowledge: (id: string, remarks?: string) => api.post<TicketView>(`/tickets/${id}/acknowledge`, { remarks }),
  resolveDirect: (id: string, resolutionNotes: string) => api.post<TicketView>(`/tickets/${id}/resolve`, { resolutionNotes }),
  assign: (id: string, providerId: string, remarks?: string) => api.post<TicketView>(`/tickets/${id}/assign`, { providerId, remarks }),
  reroute: (id: string, providerId: string, remarks?: string) => api.post<TicketView>(`/tickets/${id}/reroute`, { providerId, remarks }),
  // provider
  accept: (id: string) => api.post<TicketView>(`/tickets/${id}/accept`),
  reject: (id: string, reason?: string) => api.post<TicketView>(`/tickets/${id}/reject`, { reason }),
  providerStatus: (id: string, toStatus: string, reason?: string, resolutionNotes?: string) =>
    api.post<TicketView>(`/tickets/${id}/status`, { toStatus, reason, resolutionNotes }),
  // resident
  rebook: (id: string, providerId: string) => api.post<TicketView>(`/tickets/${id}/rebook`, { providerId }),
  approveAllocation: (id: string) => api.post<TicketView>(`/tickets/${id}/allocation/approve`, {}),
  rejectAllocation: (id: string, reason: string) =>
    api.post<TicketView>(`/tickets/${id}/allocation/reject`, { reason }),
  reopen: (id: string, remarks?: string) => api.post<TicketView>(`/tickets/${id}/reopen`, { remarks }),
  close: (id: string, rating?: number, remarks?: string) => api.post<TicketView>(`/tickets/${id}/close`, { rating, remarks }),
};

export const superadmin = {
  tenantHealth: () =>
    api.get<{ id: string; name: string; city: string | null; openTickets: number; totalTickets: number }[]>(
      "/superadmin/tenants"
    ),
  createTenant: (body: {
    name: string; city?: string; locality?: string; address?: string; pincode?: string;
    defaultTheme?: string; brandPrimaryColor?: string; reopenWindowHours?: number;
  }) => api.post<TenantCard>("/superadmin/tenants", body),
  createAdmin: (tenantId: string, phone: string, name: string) =>
    api.post<{ userId: string; tenantId: string; phoneMasked: string }>(
      `/superadmin/tenants/${tenantId}/admins`,
      { phone, name }
    ),
  updateTenant: (
    tenantId: string,
    body: Partial<{
      name: string; city: string; locality: string; address: string; pincode: string;
      logoUrl: string; defaultTheme: string; brandPrimaryColor: string;
      reopenWindowHours: number; requireAllocationApproval: boolean;
      categoryAdmin: "SUPER_ADMIN" | "COMMUNITY"; directServiceEnabled: boolean;
      providerOnboardingAllowed: boolean;
    }>
  ) => api.put<CommunitySettings>(`/superadmin/tenants/${tenantId}`, body),
  tenantAdmins: (tenantId: string) =>
    api.get<AdminAssignment[]>(`/superadmin/tenants/${tenantId}/admins`),
  attachAdmin: (tenantId: string, adminUserId: string) =>
    api.post<void>(`/superadmin/tenants/${tenantId}/admins/${adminUserId}`, {}),
  detachAdmin: (tenantId: string, adminUserId: string) =>
    api.del<void>(`/superadmin/tenants/${tenantId}/admins/${adminUserId}`),
  tenantInvites: (tenantId: string) =>
    api.get<InviteView[]>(`/superadmin/tenants/${tenantId}/invite-codes`),
  createTenantInvite: (tenantId: string, body?: { flatId?: string; validDays?: number; maxUses?: number }) =>
    api.post<InviteView>(`/superadmin/tenants/${tenantId}/invite-codes`, body ?? {}),
  revokeTenantInvite: (tenantId: string, codeId: string) =>
    api.del<void>(`/superadmin/tenants/${tenantId}/invite-codes/${codeId}`),
};

export const admin = {
  flats: () => api.get<FlatView[]>("/admin/flats"),
  createFlat: (body: { locationId: string; block?: string; flatNumber: string; addressText?: string }) =>
    api.post<FlatView>("/admin/flats", body),
  updateFlat: (id: string, body: Partial<{ locationId: string; addressText: string; geoLat: number; geoLng: number }>) =>
    api.put<FlatView>(`/admin/flats/${id}`, body),
  locations: (includeInactive = false) =>
    api.get<LocationView[]>("/admin/locations", { query: { includeInactive } }),
  createLocation: (body: { label: string; address?: string; geoLat?: number; geoLng?: number; pincode?: string }) =>
    api.post<LocationView>("/admin/locations", body),
  updateLocation: (id: string, body: Partial<{ label: string; address: string; pincode: string }>) =>
    api.put<LocationView>(`/admin/locations/${id}`, body),
  deactivateLocation: (id: string) => api.post<LocationView>(`/admin/locations/${id}/deactivate`, {}),
  reactivateLocation: (id: string) => api.post<LocationView>(`/admin/locations/${id}/reactivate`, {}),
  members: () => api.get<MemberView[]>("/admin/members"),
  memberRemovalCheck: (userId: string) => api.get<RemovalCheck>(`/admin/members/${userId}/removal-check`),
  removeMember: (userId: string) => api.post<void>(`/admin/members/${userId}/remove`, {}),
  invites: () => api.get<InviteView[]>("/admin/invite-codes"),
  createInvite: (body: { flatId?: string; relation?: string; validDays?: number; maxUses?: number }) =>
    api.post<InviteView>("/admin/invite-codes", body),
  revokeInvite: (id: string) => api.del<InviteView>(`/admin/invite-codes/${id}`),
  joinRequests: () => api.get<JoinRequestView[]>("/admin/join-requests"),
  approveJoin: (id: string, flatId?: string) => api.post<void>(`/admin/join-requests/${id}/approve`, { flatId }),
  rejectJoin: (id: string) => api.post<void>(`/admin/join-requests/${id}/reject`),
  communitySettings: () => api.get<CommunitySettings>("/admin/community-settings"),
  updateCommunitySettings: (body: Partial<{
    reopenWindowHours: number; requireAllocationApproval: boolean; directServiceEnabled: boolean;
  }>) => api.put<CommunitySettings>("/admin/community-settings", body),
  providers: (opts?: { sort?: "rating"; includeInactive?: boolean }) =>
    api.get<ProviderView[]>("/admin/providers", { query: { sort: opts?.sort, includeInactive: opts?.includeInactive } }),
  // MVP-7: browse the global verified directory and enrol into this community.
  providerCatalog: (vendorCategoryId?: string) =>
    api.get<ProviderView[]>("/admin/providers/catalog", { query: { vendorCategoryId } }),
  enrolProvider: (id: string) => api.post<ProviderView>(`/admin/providers/${id}/enrol`, {}),
  deactivateProvider: (id: string) => api.post<ProviderView>(`/admin/providers/${id}/deactivate`, {}),
  reactivateProvider: (id: string) => api.post<ProviderView>(`/admin/providers/${id}/reactivate`, {}),
};

export const providerProfile = {
  get: () => api.get<ProviderProfile>("/provider/profile"),
  update: (body: Partial<{ contactEmail: string; serviceArea: string; availability: string; availabilityNote: string }>) =>
    api.put<ProviderProfile>("/provider/profile", body),
};
