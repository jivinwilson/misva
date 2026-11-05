"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiFetch, type InviteResult, type TokenPair } from "@/lib/api";
import { getAccessToken, setSession } from "@/lib/session";

export interface InviteFormState {
  result?: InviteResult;
  error?: string;
  fieldErrors?: Record<string, string>;
  values?: Record<string, string>;
}

async function token() {
  const t = await getAccessToken();
  if (!t) redirect("/auth/refresh?next=/dashboard/team");
  return t;
}

export async function inviteEmployeeAction(_prev: InviteFormState, fd: FormData): Promise<InviteFormState> {
  const text = (k: string) => String(fd.get(k) ?? "").trim();
  const values = { firstName: text("firstName"), lastName: text("lastName"), email: text("email"), jobTitle: text("jobTitle") };
  const roleId = text("roleId");
  const allBranches = fd.get("allBranches") === "on";
  const branchIds = fd.getAll("branchIds").map(String);

  const fieldErrors: Record<string, string> = {};
  if (!values.firstName) fieldErrors.firstName = "Enter a first name.";
  if (!values.lastName) fieldErrors.lastName = "Enter a last name.";
  if (!values.email) fieldErrors.email = "Enter an email address.";
  if (!roleId) fieldErrors.roleId = "Choose a role.";
  if (!allBranches && branchIds.length === 0) fieldErrors.branchIds = "Choose at least one branch, or all branches.";
  if (Object.keys(fieldErrors).length) return { fieldErrors, values };

  try {
    const result = await apiFetch<InviteResult>("/employees/invitations", {
      method: "POST",
      token: await token(),
      body: JSON.stringify({ ...values, roleIds: [roleId], allBranches, branchIds: allBranches ? [] : branchIds }),
    });
    revalidatePath("/dashboard/team");
    return { result, values };
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/team");
      if (e.problem.errors?.length) {
        return { fieldErrors: Object.fromEntries(e.problem.errors.map((x) => [x.field, x.message])), values };
      }
      return { error: e.message, values };
    }
    return { error: "Cannot reach the server. Please try again.", values };
  }
}

export async function resendInvitationAction(prev: InviteFormState, fd: FormData): Promise<InviteFormState> {
  try {
    const result = await apiFetch<InviteResult>(`/employees/${encodeURIComponent(String(fd.get("id")))}/resend`, {
      method: "POST",
      token: await token(),
    });
    revalidatePath("/dashboard/team");
    return { result };
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/team");
    return { error: e instanceof ApiError ? e.message : "Cannot reach the server. Please try again." };
  }
}

export async function revokeInvitationAction(fd: FormData) {
  try {
    await apiFetch(`/employees/${encodeURIComponent(String(fd.get("id")))}/revoke`, { method: "POST", token: await token() });
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/team");
  }
  revalidatePath("/dashboard/team");
}

export interface AcceptFormState {
  error?: string;
  fieldErrors?: Record<string, string>;
}

export async function acceptInvitationAction(_prev: AcceptFormState, fd: FormData): Promise<AcceptFormState> {
  const inviteToken = String(fd.get("token") ?? "");
  const password = String(fd.get("password") ?? "");
  const confirm = fd.get("confirmPassword");
  const isNew = fd.get("existingAccount") !== "true";

  if (isNew) {
    if (password.length < 10) return { fieldErrors: { password: "Use at least 10 characters." } };
    if (password.length > 72) return { fieldErrors: { password: "Use at most 72 characters." } };
    if (password !== confirm) return { fieldErrors: { confirmPassword: "Passwords do not match." } };
  } else if (!password) {
    return { fieldErrors: { password: "Enter your password." } };
  }

  let tokens: TokenPair;
  try {
    tokens = await apiFetch<TokenPair>("/invitations/accept", {
      method: "POST",
      body: JSON.stringify({ token: inviteToken, password }),
    });
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.problem.code === "INVALID_CREDENTIALS") return { fieldErrors: { password: e.message } };
      return { error: e.message };
    }
    return { error: "Cannot reach the server. Please try again." };
  }
  await setSession(tokens);
  redirect("/dashboard");
}

export interface MemberFormState {
  ok?: boolean;
  error?: string;
  fieldErrors?: Record<string, string>;
}

function memberFailure(e: unknown): MemberFormState {
  if (e instanceof ApiError) {
    if (e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/team");
    if (e.problem.errors?.length) {
      return { fieldErrors: Object.fromEntries(e.problem.errors.map((x) => [x.field, x.message])) };
    }
    return { error: e.message };
  }
  return { error: "Cannot reach the server. Please try again." };
}

export async function updateEmployeeAction(_prev: MemberFormState, fd: FormData): Promise<MemberFormState> {
  const text = (k: string) => String(fd.get(k) ?? "").trim();
  const allBranches = fd.get("allBranches") === "on";
  const branchIds = fd.getAll("branchIds").map(String);
  const roleIds = fd.getAll("roleIds").map(String);

  const fieldErrors: Record<string, string> = {};
  if (!text("firstName")) fieldErrors.firstName = "Enter a first name.";
  if (!text("lastName")) fieldErrors.lastName = "Enter a last name.";
  if (fd.get("rolesLocked") !== "true" && roleIds.length === 0) fieldErrors.roleIds = "Choose at least one role.";
  if (!allBranches && branchIds.length === 0) fieldErrors.branchIds = "Choose at least one branch, or all branches.";
  if (Object.keys(fieldErrors).length) return { fieldErrors };

  try {
    await apiFetch(`/employees/${encodeURIComponent(text("id"))}`, {
      method: "PUT",
      token: await token(),
      body: JSON.stringify({
        firstName: text("firstName"),
        lastName: text("lastName"),
        jobTitle: text("jobTitle"),
        roleIds,
        allBranches,
        branchIds: allBranches ? [] : branchIds,
      }),
    });
  } catch (e) {
    return memberFailure(e);
  }
  revalidatePath("/dashboard/team");
  return { ok: true };
}

export async function memberStatusAction(_prev: MemberFormState, fd: FormData): Promise<MemberFormState> {
  const op = String(fd.get("op"));
  if (!["suspend", "reactivate", "remove"].includes(op)) return { error: "Unknown action." };
  try {
    await apiFetch(`/employees/${encodeURIComponent(String(fd.get("id")))}/${op}`, { method: "POST", token: await token() });
  } catch (e) {
    return memberFailure(e);
  }
  revalidatePath("/dashboard/team");
  return { ok: true };
}
