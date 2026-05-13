export interface User {
  id: string;
  name: string;
  email: string;
  role: 'BUYER' | 'SELLER' | 'ADMIN';
}

export interface Product {
  id: string;
  name: string;
  price: number;
  vendor_id: string;
}
