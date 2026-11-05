"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useActionState, useId, useState } from "react";
import { inviteEmployeeAction, type InviteFormState } from "@/app/actions/employees";
import { Field, FormError, SubmitButton } from "@/components/form-controls";
import type { BranchView, RoleView } from "@/lib/api";

const initial: InviteFormState = {};

export function InviteDialog({ roles, branches, canGrantAllBranches, triggerClassName }: {
  roles: RoleView[];
  branches: BranchView[];
  canGrantAllBranches: boolean;
  triggerClassName: string;
}) {
  const [open, setOpen] = useState(false);
  const [formKey, setFormKey] = useState(0);

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) setFormKey((k) => k + 1);
      }}
    >
      <Dialog.Trigger className={triggerClassName}>Invite employee</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-obsidian/60 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[min(92vw,36rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-ivory p-6 shadow-2xl focus:outline-none">
          <Dialog.Title className="font-serif text-2xl text-obsidian">Invite an employee</Dialog.Title>
          <Dialog.Description className="mt-1 text-sm text-obsidian/70">
            They choose a password and join with the role and branches you set. Invitations expire after 7 days.
          </Dialog.Description>
          <InviteForm key={formKey} roles={roles} branches={branches} canGrantAllBranches={canGrantAllBranches} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function InviteForm({ roles, branches, canGrantAllBranches }: {
  roles: RoleView[];
  branches: BranchView[];
  canGrantAllBranches: boolean;
}) {
  const [state, action] = useActionState(inviteEmployeeAction, initial);
  const [all, setAll] = useState(false);
  const roleGroup = useId();
  const v = state.values ?? {};
  const e = state.fieldErrors ?? {};

  if (state.result) {
    return (
      <div className="mt-6 space-y-4">
        <p role="status" className="rounded-lg bg-emerald/10 px-4 py-3 text-sm text-emerald">
          Invitation created for <strong>{state.result.email}</strong>.
        </p>
        {state.result.inviteUrl ? (
          <div>
            <label htmlFor="invite-link" className="mb-1.5 block text-sm font-medium text-obsidian">
              Invitation link
            </label>
            <input
              id="invite-link"
              readOnly
              value={state.result.inviteUrl}
              onFocus={(ev) => ev.currentTarget.select()}
              className="block w-full rounded-lg border border-obsidian/20 bg-white px-3.5 py-2.5 text-sm text-obsidian"
            />
            <p className="mt-1.5 text-xs text-obsidian/60">
              Email delivery is not set up yet, so share this link with them yourself. It works once.
            </p>
          </div>
        ) : (
          <p className="text-sm text-obsidian/70">Email delivery is not set up yet, so ask an administrator for the link.</p>
        )}
        <div className="flex justify-end">
          <Dialog.Close className="rounded-lg bg-obsidian px-5 py-2.5 text-sm font-medium text-ivory transition hover:bg-emerald focus:outline-none focus-visible:ring-2 focus-visible:ring-gold">
            Done
          </Dialog.Close>
        </div>
      </div>
    );
  }

  return (
    <form action={action} className="mt-6 space-y-4" noValidate>
      <FormError message={state.error} />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="First name" name="firstName" required defaultValue={v.firstName} error={e.firstName} />
        <Field label="Last name" name="lastName" required defaultValue={v.lastName} error={e.lastName} />
      </div>
      <Field label="Email" name="email" type="email" required defaultValue={v.email} error={e.email} />
      <Field label="Job title" name="jobTitle" defaultValue={v.jobTitle} />

      <fieldset>
        <legend className="mb-1.5 text-sm font-medium text-obsidian">Role</legend>
        <div className="space-y-2">
          {roles.map((r) => (
            <label key={r.id} className="flex items-center gap-3 rounded-lg border border-obsidian/15 bg-white px-3.5 py-2.5 text-sm">
              <input type="radio" name="roleId" value={r.id} className="accent-emerald" aria-describedby={e.roleId ? roleGroup : undefined} />
              {r.name}
            </label>
          ))}
        </div>
        {e.roleId ? <p id={roleGroup} className="mt-1.5 text-sm text-red-700">{e.roleId}</p> : null}
      </fieldset>

      <fieldset>
        <legend className="mb-1.5 text-sm font-medium text-obsidian">Branch access</legend>
        {canGrantAllBranches ? (
          <label className="mb-2 flex items-center gap-3 text-sm">
            <input type="checkbox" name="allBranches" checked={all} onChange={(ev) => setAll(ev.target.checked)} className="accent-emerald" />
            All branches, including future ones
          </label>
        ) : null}
        {all ? null : (
          <div className="space-y-2">
            {branches.map((b) => (
              <label key={b.id} className="flex items-center gap-3 rounded-lg border border-obsidian/15 bg-white px-3.5 py-2.5 text-sm">
                <input type="checkbox" name="branchIds" value={b.id} className="accent-emerald" />
                {b.name} <span className="text-obsidian/50">{b.code}</span>
              </label>
            ))}
          </div>
        )}
        {e.branchIds ? <p className="mt-1.5 text-sm text-red-700">{e.branchIds}</p> : null}
      </fieldset>

      <div className="flex justify-end gap-3 pt-2">
        <Dialog.Close className="rounded-lg border border-obsidian/20 px-4 py-2.5 text-sm text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-gold">
          Cancel
        </Dialog.Close>
        <div className="w-44">
          <SubmitButton pendingLabel="Sending…">Send invitation</SubmitButton>
        </div>
      </div>
    </form>
  );
}
