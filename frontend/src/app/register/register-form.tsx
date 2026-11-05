"use client";

import { useActionState } from "react";
import { registerAction, type FormState } from "@/app/actions/auth";
import { Field, FormError, SelectField, SubmitButton } from "@/components/form-controls";
import { COUNTRIES, INDUSTRIES } from "@/lib/reference-data";

const initial: FormState = {};

export function RegisterForm() {
  const [state, action] = useActionState(registerAction, initial);
  const v = state.values ?? {};
  const e = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormError message={state.error} />
      <Field label="Business name" name="businessName" autoComplete="organization" required
        defaultValue={v.businessName} error={e.businessName} />
      <div className="grid gap-5 sm:grid-cols-2">
        <SelectField label="Industry" name="industry" required defaultValue={v.industry ?? ""} error={e.industry}>
          <option value="" disabled>Select…</option>
          {INDUSTRIES.map((i) => (
            <option key={i.code} value={i.code}>{i.label}</option>
          ))}
        </SelectField>
        <SelectField label="Country" name="countryCode" required defaultValue={v.countryCode ?? "IN"} error={e.countryCode}>
          {COUNTRIES.map((c) => (
            <option key={c.code} value={c.code}>{c.name}</option>
          ))}
        </SelectField>
      </div>
      <p className="-mt-2 text-xs text-obsidian/60">
        The industry cannot be changed after you register.
      </p>
      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="First name" name="ownerFirstName" autoComplete="given-name" required
          defaultValue={v.ownerFirstName} error={e.ownerFirstName} />
        <Field label="Last name" name="ownerLastName" autoComplete="family-name" required
          defaultValue={v.ownerLastName} error={e.ownerLastName} />
      </div>
      <Field label="Work email" name="email" type="email" autoComplete="email" required
        defaultValue={v.email} error={e.email} />
      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="Password" name="password" type="password" autoComplete="new-password" required
          error={e.password} hint="At least 10 characters." />
        <Field label="Confirm password" name="confirmPassword" type="password" autoComplete="new-password" required
          error={e.confirmPassword} />
      </div>
      <SubmitButton pendingLabel="Creating your business…">Create business</SubmitButton>
    </form>
  );
}
