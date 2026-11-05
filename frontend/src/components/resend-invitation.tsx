"use client";

import { useActionState } from "react";
import { resendInvitationAction, type InviteFormState } from "@/app/actions/employees";

const initial: InviteFormState = {};

export function ResendInvitation({ membershipId, className }: { membershipId: string; className: string }) {
  const [state, action, pending] = useActionState(resendInvitationAction, initial);
  return (
    <form action={action} className="flex flex-col items-end gap-1">
      <input type="hidden" name="id" value={membershipId} />
      <button type="submit" disabled={pending} className={className}>
        {pending ? "Resending…" : "Resend"}
      </button>
      {state.error ? <span role="alert" className="text-xs text-red-700">{state.error}</span> : null}
      {state.result?.inviteUrl ? (
        <input
          readOnly
          aria-label="New invitation link"
          value={state.result.inviteUrl}
          onFocus={(ev) => ev.currentTarget.select()}
          className="w-64 rounded border border-obsidian/20 bg-white px-2 py-1 text-xs"
        />
      ) : state.result ? (
        <span role="status" className="text-xs text-emerald">New invitation created.</span>
      ) : null}
    </form>
  );
}
