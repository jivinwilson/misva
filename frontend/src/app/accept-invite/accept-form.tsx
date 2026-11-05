"use client";

import { useActionState } from "react";
import { acceptInvitationAction, type AcceptFormState } from "@/app/actions/employees";
import { Field, FormError, SubmitButton } from "@/components/form-controls";

const initial: AcceptFormState = {};

export function AcceptForm({ token, existingAccount }: { token: string; existingAccount: boolean }) {
  const [state, action] = useActionState(acceptInvitationAction, initial);
  const e = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-5" noValidate>
      <input type="hidden" name="token" value={token} />
      <input type="hidden" name="existingAccount" value={String(existingAccount)} />
      <FormError message={state.error} />
      <Field
        label={existingAccount ? "Your password" : "Choose a password"}
        name="password"
        type="password"
        autoComplete={existingAccount ? "current-password" : "new-password"}
        required
        error={e.password}
        hint={existingAccount ? undefined : "At least 10 characters."}
      />
      {existingAccount ? null : (
        <Field label="Confirm password" name="confirmPassword" type="password" autoComplete="new-password" required
          error={e.confirmPassword} />
      )}
      <SubmitButton pendingLabel="Joining…">{existingAccount ? "Accept invitation" : "Create account and join"}</SubmitButton>
    </form>
  );
}
