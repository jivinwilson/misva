"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useActionState, useEffect, useState } from "react";
import { createBranchAction, updateBranchAction, type BranchFormState } from "@/app/actions/branches";
import { Field, FormError, SelectField, SubmitButton } from "@/components/form-controls";
import type { BranchView } from "@/lib/api";
import { COUNTRIES } from "@/lib/reference-data";

const initial: BranchFormState = {};

export function BranchDialog({ branch, triggerLabel, triggerClassName }: {
  branch?: BranchView;
  triggerLabel: string;
  triggerClassName: string;
}) {
  const [open, setOpen] = useState(false);
  const editing = Boolean(branch);

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger className={triggerClassName}>{triggerLabel}</Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-obsidian/60 backdrop-blur-sm" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[min(92vw,34rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-ivory p-6 shadow-2xl focus:outline-none">
          <Dialog.Title className="font-serif text-2xl text-obsidian">
            {editing ? `Edit ${branch?.name}` : "Add a branch"}
          </Dialog.Title>
          <Dialog.Description className="mt-1 text-sm text-obsidian/70">
            {editing ? "Update this branch's details." : "A new location for your business."}
          </Dialog.Description>
          <BranchForm branch={branch} onDone={() => setOpen(false)} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function BranchForm({ branch, onDone }: { branch?: BranchView; onDone: () => void }) {
  const [state, action] = useActionState(branch ? updateBranchAction : createBranchAction, initial);
  const v = state.values ?? {};
  const e = state.fieldErrors ?? {};
  const currentCountry = COUNTRIES.some((c) => c.code === branch?.countryCode) ? branch?.countryCode : "";

  useEffect(() => {
    if (state.ok) onDone();
  }, [state.ok, onDone]);

  return (
    <form action={action} className="mt-6 space-y-4" noValidate>
      <FormError message={state.error} />
      {branch ? (
        <>
          <input type="hidden" name="id" value={branch.id} />
          <input type="hidden" name="version" value={branch.version} />
        </>
      ) : null}
      <Field label="Branch name" name="name" required defaultValue={v.name ?? branch?.name} error={e.name} />
      {branch ? null : (
        <Field
          label="Code"
          name="code"
          required
          maxLength={12}
          defaultValue={v.code}
          error={e.code}
          hint="Short and unique, for example KOCHI. It cannot be changed later."
        />
      )}
      <Field label="Address" name="addressLine1" autoComplete="off"
        defaultValue={v.addressLine1 ?? branch?.addressLine1} error={e.addressLine1} />
      <Field label="Address line 2" name="addressLine2" autoComplete="off"
        defaultValue={v.addressLine2 ?? branch?.addressLine2} error={e.addressLine2} />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="City" name="city" defaultValue={v.city ?? branch?.city} error={e.city} />
        <Field label="State / region" name="region" defaultValue={v.region ?? branch?.region} error={e.region} />
        <Field label="Postal code" name="postalCode" defaultValue={v.postalCode ?? branch?.postalCode} error={e.postalCode} />
        <SelectField label="Country" name="countryCode" defaultValue={v.countryCode ?? currentCountry ?? ""} error={e.countryCode}>
          <option value="">Same as business</option>
          {COUNTRIES.map((c) => (
            <option key={c.code} value={c.code}>{c.name}</option>
          ))}
        </SelectField>
      </div>
      <div className="flex justify-end gap-3 pt-2">
        <Dialog.Close className="rounded-lg border border-obsidian/20 px-4 py-2.5 text-sm text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-gold">
          Cancel
        </Dialog.Close>
        <div className="w-40">
          <SubmitButton pendingLabel="Saving…">{branch ? "Save changes" : "Add branch"}</SubmitButton>
        </div>
      </div>
    </form>
  );
}
