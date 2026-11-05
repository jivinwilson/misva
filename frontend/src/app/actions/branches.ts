"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import { COUNTRIES } from "@/lib/reference-data";
import { getAccessToken } from "@/lib/session";

export interface BranchFormState {
  ok?: boolean;
  error?: string;
  fieldErrors?: Record<string, string>;
  values?: Record<string, string>;
}

const FIELDS = ["name", "code", "addressLine1", "addressLine2", "city", "region", "postalCode", "countryCode"];

function read(fd: FormData) {
  return Object.fromEntries(FIELDS.map((k) => [k, String(fd.get(k) ?? "").trim()]));
}

async function token() {
  const t = await getAccessToken();
  if (!t) redirect("/auth/refresh?next=/dashboard/branches");
  return t;
}

function failure(e: unknown, values: Record<string, string>): BranchFormState {
  if (e instanceof ApiError) {
    if (e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/branches");
    if (e.problem.code === "BRANCH_CODE_TAKEN") return { fieldErrors: { code: e.message }, values };
    if (e.problem.errors?.length) {
      return { fieldErrors: Object.fromEntries(e.problem.errors.map((x) => [x.field, x.message])), values };
    }
    return { error: e.message, values };
  }
  return { error: "Cannot reach the server. Please try again.", values };
}

function body(values: Record<string, string>) {
  const country = COUNTRIES.find((c) => c.code === values.countryCode);
  return {
    name: values.name,
    addressLine1: values.addressLine1,
    addressLine2: values.addressLine2,
    city: values.city,
    region: values.region,
    postalCode: values.postalCode,
    countryCode: country?.code,
    timezone: country?.timezone,
  };
}

export async function createBranchAction(_prev: BranchFormState, fd: FormData): Promise<BranchFormState> {
  const values = read(fd);
  const fieldErrors: Record<string, string> = {};
  if (!values.name) fieldErrors.name = "Enter a branch name.";
  if (!values.code) fieldErrors.code = "Enter a short code, for example KOCHI.";
  if (Object.keys(fieldErrors).length) return { fieldErrors, values };
  try {
    await apiFetch("/branches", {
      method: "POST",
      token: await token(),
      body: JSON.stringify({ ...body(values), code: values.code }),
    });
  } catch (e) {
    return failure(e, values);
  }
  revalidatePath("/dashboard/branches");
  revalidatePath("/dashboard");
  return { ok: true };
}

export async function updateBranchAction(_prev: BranchFormState, fd: FormData): Promise<BranchFormState> {
  const values = read(fd);
  const id = String(fd.get("id") ?? "");
  const version = Number(fd.get("version"));
  if (!values.name) return { fieldErrors: { name: "Enter a branch name." }, values };
  try {
    await apiFetch(`/branches/${encodeURIComponent(id)}`, {
      method: "PUT",
      token: await token(),
      body: JSON.stringify({ ...body(values), version }),
    });
  } catch (e) {
    return failure(e, values);
  }
  revalidatePath("/dashboard/branches");
  revalidatePath("/dashboard");
  return { ok: true };
}

export async function setBranchActiveAction(fd: FormData) {
  const id = String(fd.get("id") ?? "");
  const action = fd.get("active") === "true" ? "activate" : "deactivate";
  try {
    await apiFetch(`/branches/${encodeURIComponent(id)}/${action}`, { method: "POST", token: await token() });
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/branches");
  }
  revalidatePath("/dashboard/branches");
  revalidatePath("/dashboard");
}
