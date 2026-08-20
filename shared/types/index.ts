// ==========================================
// Vendora Shared TypeScript DTOs & Models
// ==========================================

// --- Auth & User Types ---
export type UserRole = 'BUYER' | 'SELLER' | 'ADMIN';

export interface User {
  id: number | string;
  name: string;
  email: string;
  avatar_url?: string | null;
  avatarUrl?: string | null;
  enabled: boolean;
  is_banned?: boolean;
  isBanned?: boolean;
  roles: UserRole[];
  role?: UserRole;
  created_at?: string;
  createdAt?: string;
  updated_at?: string;
  updatedAt?: string;
  deleted_at?: string | null;
  deletedAt?: string | null;
}

export interface AuthResponse {
  token: string;
  accessToken?: string;
  refreshToken: string;
  id: number | string;
  name: string;
  email: string;
  roles: string[];
}

export interface TokenValidationResponse {
  valid: boolean;
  user_id?: number | string;
  userId?: number | string;
  email?: string;
  name?: string;
  role?: UserRole | string;
  roles?: string[];
  message?: string;
}

export interface UserBan {
  id: number;
  userId: number;
  reason?: string;
  bannedAt: string;
  bannedBy?: number;
}

// --- Vendor Types ---
export type VendorStatus = 'pending' | 'approved' | 'suspended' | 'rejected';

export interface Vendor {
  id: number | string;
  user_id: number | string;
  store_name: string;
  slug: string;
  description?: string | null;
  logo_url?: string | null;
  banner_url?: string | null;
  status: VendorStatus;
  commission_rate: number;
  rating?: number;
  total_sales?: number;
  created_at?: string;
  updated_at?: string;
}

export interface VendorApplication {
  id: number | string;
  user_id: number | string;
  store_name: string;
  description: string;
  status: VendorStatus;
  rejection_reason?: string | null;
  created_at?: string;
  updated_at?: string;
}

// --- Product & Catalog Types ---
export type ProductType = 'physical' | 'digital';
export type ProductStatus = 'draft' | 'pending_approval' | 'active' | 'flagged' | 'inactive';

export interface Category {
  id: number | string;
  name: string;
  slug: string;
  description?: string | null;
  parent_id?: number | string | null;
  subcategories?: Subcategory[];
  created_at?: string;
  updated_at?: string;
}

export interface Subcategory {
  id: number | string;
  category_id: number | string;
  name: string;
  slug: string;
  created_at?: string;
  updated_at?: string;
}

export interface ProductImage {
  id: number | string;
  product_id: number | string;
  image_url: string;
  is_primary: boolean;
  display_order?: number;
  created_at?: string;
}

export interface ProductTag {
  id: number | string;
  product_id: number | string;
  tag: string;
}

export interface DigitalFile {
  id: number | string;
  product_id: number | string;
  file_key: string;
  file_size_bytes: number;
  file_format?: string;
  version?: string;
  created_at?: string;
}

export interface Inventory {
  id: number | string;
  product_id: number | string;
  quantity: number;
  sku: string;
  low_stock_threshold?: number;
  updated_at?: string;
}

export interface Product {
  id: number | string;
  vendor_id: number | string;
  category_id: number | string;
  subcategory_id?: number | string | null;
  name: string;
  slug?: string;
  description: string;
  short_description?: string | null;
  price: number;
  compare_at_price?: number | null;
  cost_price?: number | null;
  sku: string;
  type: ProductType;
  status: ProductStatus;
  weight?: number | null;
  dimensions?: {
    length?: number;
    width?: number;
    height?: number;
    unit?: string;
  } | null;
  category?: Category;
  vendor?: Vendor;
  images?: ProductImage[];
  reviews?: Review[];
  inventory?: Inventory;
  avg_rating?: number;
  total_reviews?: number;
  created_at?: string;
  updated_at?: string;
}

export interface ProductFilterParams {
  category_id?: number | string;
  vendor_id?: number | string;
  type?: ProductType;
  status?: ProductStatus;
  min_price?: number;
  max_price?: number;
  rating?: number;
  search?: string;
  sort_by?: 'price_asc' | 'price_desc' | 'rating' | 'newest' | 'relevance';
  page?: number;
  per_page?: number;
}

// --- Review & Wishlist Types ---
export interface Review {
  id: number | string;
  product_id: number | string;
  user_id: number | string;
  user_name?: string;
  rating: number; // 1 to 5
  title?: string | null;
  comment?: string | null;
  verified_purchase?: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface Wishlist {
  id: number | string;
  user_id: number | string;
  product_id: number | string;
  product?: Product;
  created_at?: string;
}

// --- Cart & Order Types ---
export interface CartItem {
  id: number | string;
  cart_id: number | string;
  product_id: number | string;
  quantity: number;
  unit_price: number;
  product?: Product;
  created_at?: string;
  updated_at?: string;
}

export interface Cart {
  id: number | string;
  user_id: number | string;
  items: CartItem[];
  subtotal: number;
  total: number;
  updated_at?: string;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REVIEWED';
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED_REFUNDED' | 'RESOLVED_DENIED';
export type PayoutStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED';

export interface OrderItem {
  id: number | string;
  order_id: number | string;
  product_id: number | string;
  vendor_id: number | string;
  product_name: string;
  unit_price: number;
  quantity: number;
  total_price: number;
  product_type: ProductType;
  digital_license_key?: string | null;
}

export interface Order {
  id: number | string;
  user_id: number | string;
  status: OrderStatus;
  subtotal: number;
  shipping_fee: number;
  tax: number;
  total_amount: number;
  shipping_address?: {
    street: string;
    city: string;
    state?: string;
    zip_code: string;
    country: string;
  };
  items: OrderItem[];
  payment_intent_id?: string;
  payment_status?: PaymentStatus;
  created_at: string;
  updated_at?: string;
}

export interface Dispute {
  id: number | string;
  order_id: number | string;
  user_id: number | string;
  reason: string;
  status: DisputeStatus;
  resolution_notes?: string;
  created_at: string;
  resolved_at?: string;
}

export interface Refund {
  id: number | string;
  order_id: number | string;
  amount: number;
  reason?: string;
  stripe_refund_id?: string;
  created_at: string;
}

export interface Payout {
  id: number | string;
  vendor_id: number | string;
  amount: number;
  status: PayoutStatus;
  period_start: string;
  period_end: string;
  processed_at?: string;
}

// --- Chat & Realtime Types ---
export interface Message {
  id: string;
  conversation_id: string;
  sender_id: number | string;
  recipient_id: number | string;
  content: string;
  read: boolean;
  read_at?: string;
  created_at: string;
}

export interface Conversation {
  id: string;
  participants: (number | string)[];
  last_message?: string;
  last_message_at?: string;
  unread_count?: number;
  created_at: string;
  updated_at: string;
}

export interface TypingEvent {
  conversation_id: string;
  user_id: number | string;
  is_typing: boolean;
}

// --- Storage Types ---
export type BucketType = 'products' | 'avatars' | 'digital' | 'invoices';

export interface StorageUploadResponse {
  file_key: string;
  bucket: string;
  size: number;
  content_type: string;
}

export interface StorageQuotaResponse {
  vendor_id: string;
  usage_bytes: number;
  limit_bytes: number;
}

export interface PresignedUrlResponse {
  url: string;
}

// --- AI Ranking Types ---
export interface RankRequest {
  user_id: number | string;
  product_ids: (number | string)[];
}

export interface RankedProductItem {
  product_id: number | string;
  score: number;
}

export interface RankResponse {
  user_id: number | string;
  ranked_products: RankedProductItem[];
  model_version?: string;
}

export interface ModelStats {
  model_name: string;
  model_version: string;
  last_trained: string;
  num_features: number;
  ndcg_score?: number;
}

// --- Analytics Types ---
export interface DailyRevenue {
  date: string;
  total_revenue: number;
  total_orders: number;
  commission_revenue: number;
}

export interface TopVendorStat {
  vendor_id: number | string;
  store_name: string;
  total_sales_amount: number;
  total_orders: number;
}

export interface TrendingProductStat {
  product_id: number | string;
  product_name: string;
  views_count: number;
  purchases_count: number;
}

export interface FraudSignal {
  id: number | string;
  user_id: number | string;
  signal_type: 'velocity_spike' | 'multi_account_ip' | 'high_chargeback_rate';
  confidence: number;
  details: string;
  detected_at: string;
}

export interface AnalyticsPlatformSummary {
  gmv: number;
  active_vendors_count: number;
  active_buyers_count: number;
  open_disputes_count: number;
  total_orders_today: number;
  total_revenue_today: number;
}

