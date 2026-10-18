// Mirrors the Spring Boot DTOs. Money is always paise (integer).

export type ApiError = { code: string; message: string; details?: Record<string, unknown> };

export type Page<T> = { items: T[]; page: number; size: number; total: number; totalPages: number };

export type Me = { id: number; email: string; name: string; phone?: string; role: 'USER' | 'ADMIN'; createdAt: string };

export type LoginResponse =
  | { status: 'LOGGED_IN'; accessToken: string; expiresIn: number; user: Me }
  | { status: 'SIGNUP_REQUIRED'; signupToken: string; email: string; name?: string };

export type AttributeDef = { key: string; label: string; type: string; options?: string[]; filterable?: boolean };

export type Category = {
  id: number; parentId?: number; name: string; slug: string; description?: string; imageUrl?: string;
  attributeDefs: AttributeDef[]; sortOrder: number; active: boolean;
};

export type CategoryDetail = {
  category: Category; facets: Record<string, { value: string; count: number }[]>;
  minPricePaise?: number; maxPricePaise?: number;
};

export type ProductCard = {
  id: number; slug: string; title: string; categorySlug: string; pricePaise: number; mrpPaise: number;
  inStock: boolean; imageUrl?: string; imageAlt?: string;
};

export type Variant = {
  id: number; sku: string; title: string; attributes: Record<string, string>; mrpPaise: number; pricePaise: number;
  available: number; inStock: boolean;
};

export type ImageView = { id: number; variantId?: number; url: string; alt?: string; width?: number; height?: number };

export type ProductDetail = {
  id: number; slug: string; title: string; description: string; category: Category;
  attributes: Record<string, string>; returnable: boolean; seoTitle?: string; seoDescription?: string;
  variants: Variant[]; images: ImageView[]; updatedAt: string;
};

export type Warning = { type: string; variantId?: number; message: string };

export type QuoteLine = {
  variantId: number; productId: number; sku: string; productTitle: string; productSlug: string; variantTitle: string;
  attributes: Record<string, string>; imageUrl?: string; mrpPaise: number; unitPricePaise: number; qty: number;
  available: number; lineTotalPaise: number; discountPaise: number;
};

export type Quote = {
  lines: QuoteLine[]; subtotalPaise: number; discountPaise: number; shippingPaise: number; taxPaise: number;
  totalPaise: number; cgstPaise: number; sgstPaise: number; igstPaise: number; intraState: boolean;
  couponCode?: string; couponError?: string; freeShippingRemainingPaise?: number; warnings: Warning[];
};

export type CartView = { cartId?: number; items: { itemId: number; line: QuoteLine }[]; pricing: Quote };

export type Address = {
  id: number; name: string; phone: string; line1: string; line2?: string; landmark?: string; city: string;
  state: string; pincode: string; isDefault: boolean;
};

export type CheckoutPayment = {
  orderId: number; orderNumber: string; status: string; razorpayOrderId: string; razorpayKeyId: string;
  amountPaise: number; currency: string; customerName: string; customerEmail: string; customerPhone: string;
  payBefore: string;
};

export type OrderItem = {
  id: number; productId: number; variantId: number; sku: string; productTitle: string; variantTitle: string;
  imageUrl?: string; hsnCode: string; gstRateBp: number; mrpPaise: number; unitPricePaise: number; qty: number;
  lineTotalPaise: number; discountPaise: number; taxablePaise: number; cgstPaise: number; sgstPaise: number;
  igstPaise: number; returnable: boolean;
};

export type HistoryRow = { id: number; fromStatus?: string; toStatus: string; reason?: string; actorType: string;
  actorEmail?: string; createdAt: string };

export type RefundRow = { id: number; orderId: number; orderNumber: string; amountPaise: number; status: string;
  gatewayRefundId?: string; receipt: string; reason: string; source: string; error?: string;
  initiatedByEmail?: string; createdAt: string; processedAt?: string };

export type OrderDetail = {
  id: number; orderNumber: string; status: string; email: string; phone: string;
  shippingAddress: Record<string, string>; items: OrderItem[]; subtotalPaise: number; discountPaise: number;
  shippingPaise: number; totalPaise: number; taxablePaise: number; cgstPaise: number; sgstPaise: number;
  igstPaise: number; couponCode?: string; paymentExpiresAt?: string; paidAt?: string; shippedAt?: string;
  deliveredAt?: string; cancelledAt?: string; cancelReason?: string; createdAt: string;
  payment?: { status: string; method?: string; gatewayPaymentId?: string; capturedAt?: string };
  timeline: HistoryRow[]; refunds: RefundRow[]; canCancel: boolean; canRetryPayment: boolean;
  customerId?: number; customerName?: string;
};

export type OrderSummary = { id: number; orderNumber: string; status: string; totalPaise: number; itemCount: number;
  firstItemTitle?: string; firstItemImage?: string; createdAt: string };

export type DeliveryCheck = { pincode: string; serviceable: boolean; etaDays?: number; estimatedDelivery?: string;
  message: string };
