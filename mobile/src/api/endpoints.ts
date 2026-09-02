import { api, uploadFile } from "./client";
import {
  AdminVendorCategory, AttachmentView, Category, FlatView, InviteView, JoinRequestView, KycDocView,
  MeResponse, NotificationPreferences, OfferStatus, OfferView, Page, PaymentView, ProviderView,
  ReceiptView, RedemptionView, SessionResponse, TenantCard, TicketView, TimelineEntry,
  VendorCategory, VendorCategoryKind,
} from "./types";

export const auth = {
  requestOtp: (phone: string) =>
    api.post<{ sent: boolean; devCode: string | null }>("/auth/otp/request", { phone }, { auth: false }),
  verifyOtp: (phone: string, code: string) =>
    api.post<SessionResponse>("/auth/otp/verify", { phone, code }, { auth: false }),
  completeProfile: (name: string, email?: string) =>
    api.post<SessionResponse>("/auth/profile", { name, email }),
};

export const me = {
  get: () => api.get<MeResponse>("/me"),
  setTheme: (theme: string) => api.put<void>("/me/theme", { theme }),
  registerDevice: (token: string, platform: string, provider = "expo") =>
    api.post<void>("/me/devices", { token, platform, provider }),
  notificationPreferences: () => api.get<NotificationPreferences>("/me/notification-preferences"),
  updateNotificationPreferences: (body: Partial<NotificationPreferences>) =>
    api.put<NotificationPreferences>("/me/notification-preferences", body),
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
  forProvider: (providerId: string) => api.get<KycDocView[]>(`/admin/providers/${providerId}/kyc`),
  review: (providerId: string, docId: string, status: string, note?: string) =>
    api.post<KycDocView>(`/admin/providers/${providerId}/kyc/${docId}/review`, { status, note }),
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

export const communities = {
  search: (query?: string) => api.get<Page<TenantCard>>("/tenants", { query: { query, size: 30 } }),
  join: (tenantId: string, inviteCode?: string, requestedFlatLabel?: string) =>
    api.post<SessionResponse>("/memberships/join", { tenantId, inviteCode, requestedFlatLabel }),
};

export const catalog = {
  categories: () => api.get<Category[]>("/categories"),
  vendorCategories: () => api.get<VendorCategory[]>("/vendor-categories"),
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
    serviceLandmark?: string; preferredTimeWindow?: string; flatId?: string;
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
};

export const admin = {
  flats: () => api.get<FlatView[]>("/admin/flats"),
  createFlat: (body: { block?: string; flatNumber: string; addressText?: string }) =>
    api.post<FlatView>("/admin/flats", body),
  invites: () => api.get<InviteView[]>("/admin/invite-codes"),
  createInvite: (body: { flatId?: string; relation?: string; validDays?: number; maxUses?: number }) =>
    api.post<InviteView>("/admin/invite-codes", body),
  revokeInvite: (id: string) => api.del<InviteView>(`/admin/invite-codes/${id}`),
  joinRequests: () => api.get<JoinRequestView[]>("/admin/join-requests"),
  approveJoin: (id: string, flatId?: string) => api.post<void>(`/admin/join-requests/${id}/approve`, { flatId }),
  rejectJoin: (id: string) => api.post<void>(`/admin/join-requests/${id}/reject`),
  providers: () => api.get<ProviderView[]>("/admin/providers"),
  addProvider: (body: {
    name: string; vendorCategoryId: string; company: boolean; contactPhone: string;
    contactEmail?: string; serviceArea?: string;
  }) => api.post<ProviderView>("/admin/providers", body),
  verifyProvider: (id: string, status: string) => api.post<ProviderView>(`/admin/providers/${id}/verify`, { status }),
};
