"use client";

import { useId, type ComponentProps } from "react";
import { useFormStatus } from "react-dom";

const control =
  "block w-full rounded-lg border border-obsidian/20 bg-white px-3.5 py-2.5 text-obsidian shadow-sm outline-none transition placeholder:text-obsidian/40 focus:border-emerald focus:ring-2 focus:ring-gold/60 aria-[invalid=true]:border-red-700";

export function Field({ label, error, hint, className, ...props }: ComponentProps<"input"> & {
  label: string;
  error?: string;
  hint?: string;
}) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-obsidian">
        {label}
      </label>
      <input
        id={id}
        className={control}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      />
      {hint && !error ? (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-obsidian/60">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-red-700">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function SelectField({ label, error, className, children, ...props }: ComponentProps<"select"> & {
  label: string;
  error?: string;
}) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-obsidian">
        {label}
      </label>
      <select
        id={id}
        className={control}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...props}
      >
        {children}
      </select>
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-red-700">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function SubmitButton({ children, pendingLabel }: { children: string; pendingLabel: string }) {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      disabled={pending}
      className="w-full rounded-lg bg-obsidian px-4 py-3 font-medium text-ivory shadow-sm transition hover:bg-emerald focus:outline-none focus-visible:ring-2 focus-visible:ring-gold focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {pending ? pendingLabel : children}
    </button>
  );
}

export function FormError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <div role="alert" className="rounded-lg border border-red-700/30 bg-red-50 px-4 py-3 text-sm text-red-800">
      {message}
    </div>
  );
}
