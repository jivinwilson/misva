"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useActionState, useEffect, useState } from "react";
import {
  memberStatusAction,
  updateEmployeeAction,
  type MemberFormState,
} from "@/app/actions/employees";
import { Field, FormError, SubmitButton } from "@/components/form-controls";
import type { BranchView, EmployeeView, RoleView } from "@/lib/api";

const initial: MemberFormState = {};
const overlay = "fixed inset-0 z-40 bg-obsidian/60 backdrop-blur-sm";
const content =
  "fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[min(92vw,36rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-ivory p-6 shadow-2xl focus:outline-none";
const cancelButton =
  "rounded-lg border border-obsidian/20 px-4 py-2.5 text-sm text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-gold";

export function EditMemberDialog({ member, roles, branches, canGrantAllBranches, triggerClassName }: {
  member: EmployeeView;
  roles: RoleView[];
  branches: BranchView[];
  canGrantAllBranches: boolean;
  triggerClassName: string;
}) {
  const [open, setOpen] = useState(false);
  const name = [member.firstName, member.lastName].filter(Boolean).join(" ") || member.email;

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger className={triggerClassName}>Edit</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className={overlay} />
        <Dialog.Content className={content}>
          <Dialog.Title className="font-serif text-2xl text-obsidian">Edit {name}</Dialog.Title>
          <Dialog.Description className="mt-1 text-sm text-obsidian/70">
            {member.email}. Changes apply the next time they use MISEVA, usually immediately.
          </Dialog.Description>
          <EditForm
            member={member}
            roles={roles}
            branches={branches}
            canGrantAllBranches={canGrantAllBranches}
            onDone={() => setOpen(false)}
          />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function EditForm({ member, roles, branches, canGrantAllBranches, onDone }: {
  member: EmployeeView;
  roles: RoleView[];
  branches: BranchView[];
  canGrantAllBranches: boolean;
  onDone: () => void;
}) {
  const [state, action] = useActionState(updateEmployeeAction, initial);
  const [all, setAll] = useState(member.allBranches);
  const rolesLocked = member.roles.includes("Owner");
  const e = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok) onDone();
  }, [state.ok, onDone]);

  return (
    <form action={action} className="mt-6 space-y-4" noValidate>
      <FormError message={state.error} />
      <input type="hidden" name="id" value={member.membershipId} />
      <input type="hidden" name="rolesLocked" value={String(rolesLocked)} />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="First name" name="firstName" required defaultValue={member.firstName} error={e.firstName} />
        <Field label="Last name" name="lastName" required defaultValue={member.lastName} error={e.lastName} />
      </div>
      <Field label="Job title" name="jobTitle" defaultValue={member.jobTitle} />

      {rolesLocked ? (
        <p className="rounded-lg bg-champagne px-4 py-3 text-sm text-obsidian">
          This person is an owner. The owner role cannot be changed here.
        </p>
      ) : (
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-obsidian">Roles</legend>
          <div className="space-y-2">
            {roles.map((r) => (
              <label key={r.id} className="flex items-center gap-3 rounded-lg border border-obsidian/15 bg-white px-3.5 py-2.5 text-sm">
                <input type="checkbox" name="roleIds" value={r.id} defaultChecked={member.roleIds.includes(r.id)} className="accent-emerald" />
                {r.name}
              </label>
            ))}
          </div>
          {e.roleIds ? <p className="mt-1.5 text-sm text-red-700">{e.roleIds}</p> : null}
        </fieldset>
      )}

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
                <input type="checkbox" name="branchIds" value={b.id} defaultChecked={member.branchIds.includes(b.id)} className="accent-emerald" />
                {b.name} <span className="text-obsidian/50">{b.code}</span>
              </label>
            ))}
          </div>
        )}
        {e.branchIds ? <p className="mt-1.5 text-sm text-red-700">{e.branchIds}</p> : null}
      </fieldset>

      <div className="flex justify-end gap-3 pt-2">
        <Dialog.Close className={cancelButton}>Cancel</Dialog.Close>
        <div className="w-40">
          <SubmitButton pendingLabel="Saving…">Save changes</SubmitButton>
        </div>
      </div>
    </form>
  );
}

export function ConfirmMemberAction({ membershipId, op, triggerLabel, title, description, confirmLabel, triggerClassName, danger }: {
  membershipId: string;
  op: "suspend" | "reactivate" | "remove";
  triggerLabel: string;
  title: string;
  description: string;
  confirmLabel: string;
  triggerClassName: string;
  danger?: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger className={triggerClassName}>{triggerLabel}</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className={overlay} />
        <Dialog.Content className={`${content} w-[min(92vw,28rem)]`}>
          <Dialog.Title className="font-serif text-2xl text-obsidian">{title}</Dialog.Title>
          <Dialog.Description className="mt-2 text-sm text-obsidian/70">{description}</Dialog.Description>
          <ConfirmForm membershipId={membershipId} op={op} confirmLabel={confirmLabel} danger={danger} onDone={() => setOpen(false)} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function ConfirmForm({ membershipId, op, confirmLabel, danger, onDone }: {
  membershipId: string;
  op: string;
  confirmLabel: string;
  danger?: boolean;
  onDone: () => void;
}) {
  const [state, action] = useActionState(memberStatusAction, initial);
  useEffect(() => {
    if (state.ok) onDone();
  }, [state.ok, onDone]);

  return (
    <form action={action} className="mt-6 space-y-4">
      <FormError message={state.error} />
      <input type="hidden" name="id" value={membershipId} />
      <input type="hidden" name="op" value={op} />
      <div className="flex justify-end gap-3">
        <Dialog.Close className={cancelButton}>Cancel</Dialog.Close>
        <div className="w-40">
          {danger ? <DangerSubmit label={confirmLabel} /> : <SubmitButton pendingLabel="Working…">{confirmLabel}</SubmitButton>}
        </div>
      </div>
    </form>
  );
}

function DangerSubmit({ label }: { label: string }) {
  return (
    <button
      type="submit"
      className="w-full rounded-lg bg-red-800 px-4 py-3 font-medium text-ivory shadow-sm transition hover:bg-red-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-400 focus-visible:ring-offset-2"
    >
      {label}
    </button>
  );
}
