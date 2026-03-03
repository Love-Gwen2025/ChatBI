export interface PageResponse<T> {
  data: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}
