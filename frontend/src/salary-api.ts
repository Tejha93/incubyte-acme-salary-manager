import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface Employee {
  id: string;
  name: string;
  country: string;
  department: string;
  currency: string;
  salaryMinor: number;
  version: number;
}

export interface Page { employees: Employee[]; total: number; page: number; size: number }
export interface Options { countries: string[]; departments: string[] }
export interface Change {
  version: number;
  previousMinor: number;
  salaryMinor: number;
  reason: string;
  changedBy: string;
  changedAt: string;
}
export interface Details { employee: Employee; history: Change[] }
export interface Summary {
  currency: string;
  count: number;
  medianMinor: number;
  averageMinor: number;
  minimumMinor: number;
  maximumMinor: number;
}

@Injectable({ providedIn: 'root' })
export class SalaryApi {
  private readonly http = inject(HttpClient);

  async login(username: string, password: string) {
    await this.csrf();
    await firstValueFrom(this.http.post('/api/login', new HttpParams().set('username', username).set('password', password)));
    await this.csrf();
  }

  private csrf() { return firstValueFrom(this.http.get('/api/csrf')); }
  async logout() {
    await this.csrf();
    return firstValueFrom(this.http.post('/api/logout', null));
  }
  session() { return firstValueFrom(this.http.get<{ username: string }>('/api/session')); }
  options() { return firstValueFrom(this.http.get<Options>('/api/options')); }

  directory(search: string, country: string, department: string, page: number, size: number) {
    return firstValueFrom(this.http.get<Page>('/api/employees', { params: { search, country, department, page, size } }));
  }

  insights(country: string, department: string) {
    return firstValueFrom(this.http.get<Summary[]>('/api/insights', { params: { country, department } }));
  }

  details(id: string) {
    return firstValueFrom(this.http.get<Details>('/api/employees/' + encodeURIComponent(id)));
  }

  async revise(employee: Employee, amount: string, reason: string) {
    await this.csrf();
    return firstValueFrom(this.http.put<Details>('/api/employees/' + encodeURIComponent(employee.id) + '/salary',
      { amount, reason, version: employee.version }));
  }
}

export function errorMessage(error: unknown, fallback: string) {
  if (error instanceof HttpErrorResponse) {
    return error.error?.detail || fallback;
  }
  return fallback;
}
