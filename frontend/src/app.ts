import { A11yModule } from '@angular/cdk/a11y';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Details, Employee, Options, Page, SalaryApi, Summary, errorMessage } from './salary-api';

@Component({
  selector: 'acme-root',
  standalone: true,
  imports: [A11yModule, FormsModule, DecimalPipe, DatePipe],
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly api = inject(SalaryApi);
  private readonly formatters = new Map<string, Intl.NumberFormat>();
  private refreshVersion = 0;
  private detailVersion = 0;
  private searchTimer?: ReturnType<typeof setTimeout>;
  readonly pageSize = 12;
  ready = false;
  username = '';
  loginName = '';
  password = '';
  signingIn = false;
  loginError = '';
  view: 'directory' | 'insights' = 'directory';
  options: Options = { countries: [], departments: [] };
  country = '';
  department = '';
  search = '';
  pageNumber = 0;
  directory?: Page;
  summaries: Summary[] = [];
  loading = false;
  error = '';
  selected?: Details;
  detailLoading = false;
  detailError = '';
  amount = '';
  reason = '';
  saving = false;
  conflict = false;
  notice = '';

  async ngOnInit() {
    try {
      await this.initialize();
    } catch (error) {
      if (this.status(error) !== 401) this.loginError = 'The service could not be reached.';
    } finally {
      this.ready = true;
    }
  }

  async signIn() {
    this.signingIn = true;
    this.loginError = '';
    try {
      await this.api.login(this.loginName, this.password);
      await this.initialize();
    } catch (error) {
      this.loginError = this.status(error) === 401 ? 'Check your username and password.' : 'Sign-in is unavailable. Try again.';
    } finally {
      this.password = '';
      this.signingIn = false;
    }
  }

  async signOut() {
    try {
      await this.api.logout();
      this.clearSession();
    } catch {
      this.error = 'Sign-out failed. Try again.';
    }
  }

  private async initialize() {
    const session = await this.api.session();
    this.options = await this.api.options();
    this.username = session.username;
    await this.refresh();
  }

  changeView(view: 'directory' | 'insights') {
    this.view = view;
    this.closeDetails();
    void this.refresh();
  }

  searchChanged(value: string) {
    this.search = value;
    this.pageNumber = 0;
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => void this.refresh(), 220);
  }

  filtersChanged() {
    this.pageNumber = 0;
    void this.refresh();
  }

  goTo(page: number) {
    this.pageNumber = page;
    void this.refresh();
  }

  async refresh() {
    clearTimeout(this.searchTimer);
    const version = ++this.refreshVersion;
    this.loading = true;
    this.error = '';
    try {
      if (this.view === 'directory') {
        const data = await this.api.directory(this.search, this.country, this.department, this.pageNumber, this.pageSize);
        if (version === this.refreshVersion) this.directory = data;
      } else {
        const data = await this.api.insights(this.country, this.department);
        if (version === this.refreshVersion) this.summaries = data;
      }
    } catch (error) {
      if (version === this.refreshVersion && !this.expired(error)) {
        this.error = errorMessage(error, 'Could not load compensation data. Try again.');
      }
    } finally {
      if (version === this.refreshVersion) this.loading = false;
    }
  }

  async openDetails(id: string) {
    const version = ++this.detailVersion;
    this.detailLoading = true;
    this.detailError = '';
    this.notice = '';
    this.conflict = false;
    try {
      const details = await this.api.details(id);
      if (version !== this.detailVersion) return;
      this.selected = details;
      const digits = this.formatter(details.employee.currency).resolvedOptions().maximumFractionDigits ?? 2;
      this.amount = (details.employee.salaryMinor / 10 ** digits).toFixed(digits);
      this.reason = '';
    } catch (error) {
      if (version === this.detailVersion && !this.expired(error)) {
        this.detailError = errorMessage(error, 'Could not load the employee record.');
      }
    } finally {
      if (version === this.detailVersion) this.detailLoading = false;
    }
  }

  closeDetails() {
    ++this.detailVersion;
    this.selected = undefined;
    this.detailError = '';
    this.detailLoading = false;
    this.conflict = false;
  }

  async save(employee: Employee) {
    if (this.saving || this.conflict) return;
    const version = this.detailVersion;
    this.saving = true;
    this.detailError = '';
    this.notice = '';
    try {
      const details = await this.api.revise(employee, this.amount.trim(), this.reason.trim());
      if (version === this.detailVersion) {
        this.selected = details;
        this.reason = '';
        this.notice = 'Salary updated. The change is recorded in history.';
      }
      if (this.username) await this.refresh();
    } catch (error) {
      if (version === this.detailVersion && !this.expired(error)) {
        this.conflict = this.status(error) === 409;
        this.detailError = errorMessage(error, 'Could not save the salary. Try again.');
      }
    } finally {
      this.saving = false;
    }
  }

  money(minor: number, currency: string) {
    const formatter = this.formatter(currency);
    return formatter.format(minor / 10 ** (formatter.resolvedOptions().maximumFractionDigits ?? 2));
  }

  get insightHeadcount() {
    return this.summaries.reduce((total, row) => total + row.count, 0);
  }

  private formatter(currency: string) {
    let formatter = this.formatters.get(currency);
    if (!formatter) {
      formatter = new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'code' });
      this.formatters.set(currency, formatter);
    }
    return formatter;
  }

  private status(error: unknown) { return error instanceof HttpErrorResponse ? error.status : 0; }

  private expired(error: unknown) {
    if (this.status(error) !== 401) return false;
    this.clearSession();
    this.loginError = 'Your session ended. Sign in again.';
    return true;
  }

  private clearSession() {
    ++this.refreshVersion;
    clearTimeout(this.searchTimer);
    this.closeDetails();
    this.username = '';
    this.directory = undefined;
    this.summaries = [];
    this.search = this.country = this.department = this.notice = this.error = this.loginError = '';
    this.pageNumber = 0;
    this.view = 'directory';
  }
}
