"use client";

import { useActionState } from "react";
import { loginAction, type FormState } from "@/app/actions/auth";
import { Field, FormError, SelectField, SubmitButton } from "@/components/form-controls";

const initial: FormState = {};

export function LoginForm() {
  const [state, action] = useActionState(loginAction, initial);

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormError message={state.error} />
      <Field
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
      />
      <Field label="Password" name="password" type="password" autoComplete="current-password" required />
      {state.businesses ? (
        <SelectField label="Business" name="businessId" defaultValue={state.businesses[0]?.businessId}>
          {state.businesses.map((b) => (
            <option key={b.businessId} value={b.businessId}>
              {b.name}
            </option>
          ))}
        </SelectField>
      ) : null}
      <SubmitButton pendingLabel="Signing in…">Sign in</SubmitButton>
    </form>
  );
}
