import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { finalize, map, Observable, of, switchMap, tap } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog.component';
import { documentDigits } from '../../../shared/cpf-cnpj';
import { fieldMessage, setFormProblem, SubmissionKeys } from '../../../shared/forms/form-feedback';
import { readState } from '../../../shared/http/read-state';
import { CustomerResponse } from '../../../shared/models/billing';
import { cpfCnpjValidator } from '../../../shared/validators/cpf-cnpj.validators';
import {
  customerEmailValidator,
  customerNameValidator,
  customerPhoneValidator,
} from '../../../shared/validators/customer-contact.validators';
import { formatCustomerDocument, formatCustomerPhone } from '../customer-masks';
import { CustomerApi } from '../data-access/customer.api';

@Component({
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-form.component.html',
  styleUrl: './customer-form.component.scss',
})
export class CustomerFormComponent {
  private readonly api = inject(CustomerApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly keys = new SubmissionKeys();
  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');
  private readonly revision = signal(0);

  readonly id = input<string>();
  protected readonly saving = signal(false);
  protected readonly failure = signal('');
  protected readonly fieldMessage = fieldMessage;
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', customerNameValidator],
    document: ['', [Validators.required, cpfCnpjValidator]],
    email: ['', customerEmailValidator],
    phone: ['', customerPhoneValidator],
  });
  protected readonly customer = toSignal(
    toObservable(computed(() => ({ id: this.id(), revision: this.revision() }))).pipe(
      switchMap(({ id }) => {
        this.failure.set('');
        if (!id) {
          this.loadForm();
          return of({ status: 'ready', data: null } as const);
        }

        return readState(
          this.api.get(id).pipe(tap((customer) => this.loadForm(customer))),
          'Não foi possível carregar o cliente para edição.',
        );
      }),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  confirmExit() {
    if (this.saving()) {
      return false;
    }
    if (this.form.pristine) {
      return true;
    }
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: 'Descartar alterações?',
          message: 'As alterações deste formulário ainda não foram salvas.',
          confirmLabel: 'Descartar alterações',
        },
      })
      .afterClosed()
      .pipe(map((confirmed) => confirmed === true));
  }

  protected retryLoad(): void {
    this.revision.update((revision) => revision + 1);
  }

  protected maskInput(field: 'document' | 'phone', input: HTMLInputElement): void {
    const { value, selectionStart } = input;
    const formatted =
      field === 'document' ? formatCustomerDocument(value) : formatCustomerPhone(value);
    if (formatted === value) {
      return;
    }

    this.form.controls[field].setValue(formatted);
    const digitsBeforeCaret = value
      .slice(0, selectionStart ?? value.length)
      .replace(/\D/g, '').length;
    let caret = 0;
    let digits = 0;
    while (caret < formatted.length && digits < digitsBeforeCaret) {
      if (/\d/.test(formatted[caret])) {
        digits++;
      }
      caret++;
    }
    input.setSelectionRange(caret, caret);
  }

  protected save(): void {
    const state = this.customer();
    if (this.form.invalid || this.saving() || state.status !== 'ready') {
      return;
    }

    const { name, document, email, phone } = this.form.getRawValue();
    const contact = {
      name: name.trim(),
      email: email.trim() || null,
      phone: phone.replace(/\D/g, '') || null,
    };
    if (state.data) {
      const { id, version } = state.data;
      this.submit(this.api.update(id, { ...contact, version }), true);
    } else {
      const digits = documentDigits(document);
      if (digits) {
        const payload = { ...contact, document: digits };
        this.submit(this.api.create(payload, this.keys.forPayload(payload)), false);
      }
    }
  }

  private loadForm(customer?: CustomerResponse): void {
    this.form.reset({
      name: customer?.name ?? '',
      document: formatCustomerDocument(customer?.document ?? ''),
      email: customer?.email ?? '',
      phone: formatCustomerPhone(customer?.phone ?? ''),
    });
    if (customer) {
      this.form.controls.document.disable();
    } else {
      this.form.controls.document.enable();
    }
    afterNextRender(
      { write: () => this.nameInput()?.nativeElement.focus() },
      { injector: this.injector },
    );
  }

  private submit(request: Observable<CustomerResponse>, editing: boolean): void {
    this.failure.set('');
    this.saving.set(true);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (saved) => {
          this.form.markAsPristine();
          this.saving.set(false);
          this.snackbar.open(editing ? 'Cliente atualizado.' : 'Cliente cadastrado.', 'Fechar', {
            duration: 3000,
          });
          void this.router.navigate(['/customers', saved.id]);
        },
        error: (error: unknown) => {
          if (!(error instanceof HttpErrorResponse && error.status === 409)) {
            this.failure.set(
              setFormProblem(this.form, error, 'Não foi possível salvar o cliente.'),
            );
          }
        },
      });
  }
}
