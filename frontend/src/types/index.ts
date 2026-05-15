// User types
export interface User {
  id: number
  username: string
  email: string
  nickname: string
  avatar: string
  bio: string
  role: 'USER' | 'PUBLISHER' | 'ADMIN'
  points: number
  createdAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  nickname?: string
}

export interface LoginResponse {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
  token: string
  refreshToken: string
  expiresIn: number
}

// Resource types
export interface Category {
  id: number
  name: string
  parentId: number | null
  children?: Category[]
  sortOrder: number
}

export interface Tag {
  id: number
  name: string
  usageCount: number
}

export interface Resource {
  id: number
  title: string
  category: Category
  tags: Tag[]
  publisher: Pick<User, 'id' | 'nickname' | 'avatar'>
  description: string
  aiSummary: string
  resourceType: 'FILE' | 'LINK'
  externalUrl: string
  coverImage: string
  viewCount: number
  likeCount: number
  favoriteCount: number
  commentCount: number
  avgRating: number
  ratingCount: number
  hotScore: number
  status: 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED'
  createdAt: string
  updatedAt: string
}

export interface ResourceCreateRequest {
  title: string
  categoryId: number
  tags: string[]
  description: string
  resourceType: 'FILE' | 'LINK'
  externalUrl?: string
  files?: File[]
}

// Comment types
export interface Comment {
  id: number
  content: string
  user: Pick<User, 'id' | 'nickname' | 'avatar'>
  parentId: number | null
  replies?: Comment[]
  likeCount: number
  createdAt: string
}

// API Response types
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageData<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

// Search types
export interface SearchParams {
  keyword?: string
  categoryId?: number
  tags?: string[]
  sortBy?: 'relevance' | 'latest' | 'hot' | 'rating'
  minRating?: number
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export interface NlSearchResult {
  parsedIntent: {
    keywords: string[]
    sortBy: string
    limit: number
    filters: Record<string, any>
  }
  results: Resource[]
}

// Recommendation types
export interface Recommendation {
  resource: Resource
  recommendReason: string
  algorithm: string
  score: number
}
