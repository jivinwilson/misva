"use server";

import { redirect } from "next/navigation";
import { ApiError, apiFetch, type BusinessRef, type Registration, type TokenPair } from "@/lib/api";
import { clearSession, getRefreshToken, setSession } from "@/lib/session";
import { COUNTRIES, INDUSTRIES } from "@/lib/reference-data";

export interface FormState {
  error?: string;
  fieldErrors?: Record<string, string>;
  values?: Record<string, string>;
  businesses?: BusinessRef[];
}

const text = (fd: FormData, key: string) => String(fd.get(key) ?? "").trim();

function valuesOf(fd: FormData, keys: string[]) {
  return Object.fromEntries(keys.map((k) => [k, text(fd, k)]));
}

export async function loginAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const email = text(fd, "email");
  const password = String(fd.get("password") ?? "");
  const businessId = text(fd, "businessId");
  const values = { email, businessId };

  if (!email || !password) {
    return { error: "Enter your email and password.", values };
  }

  let tokens: TokenPair;
  try {
    tokens = await apiFetch<TokenPair>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password, businessId: businessId || undefined }),
    });
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.problem.code === "BUSINESS_SELECTION_REQUIRED") {
        return {
          error: "You belong to several businesses. Choose one and enter your password again.",
          businesses: e.problem.businesses,
          values,
        };
      }
      return { error: e.message, values };
    }
    return { error: "Cannot reach the server. Please try again.", values };
  }
  await setSession(tokens);
  redirect("/dashboard");
}

export async function registerAction(_prev: FormState, fd: FormData): Promise<FormState> {
  const keys = ["businessName", "industry", "countryCode", "ownerFirstName", "ownerLastName", "email"];
  const values = valuesOf(fd, keys);
  const password = String(fd.get("password") ?? "");
  const confirm = String(fd.get("confirmPassword") ?? "");

  const fieldErrors: Record<string, string> = {};
  if (!values.businessName) fieldErrors.businessName = "Enter your business name.";
  if (!INDUSTRIES.some((i) => i.code === values.industry)) fieldErrors.industry = "Choose your industry.";
  const country = COUNTRIES.find((c) => c.code === values.countryCode);
  if (!country) fieldErrors.countryCode = "Choose your country.";
  if (!values.ownerFirstName) fieldErrors.ownerFirstName = "Enter your first name.";
  if (!values.ownerLastName) fieldErrors.ownerLastName = "Enter your last name.";
  if (!values.email) fieldErrors.email = "Enter your email.";
  if (password.length < 10) fieldErrors.password = "Use at least 10 characters.";
  else if (password.length > 72) fieldErrors.password = "Use at most 72 characters.";
  else if (password !== confirm) fieldErrors.confirmPassword = "Passwords do not match.";
  if (Object.keys(fieldErrors).length > 0 || !country) return { fieldErrors, values };

  let result: Registration;
  try {
    result = await apiFetch<Registration>("/businesses/register", {
      method: "POST",
      body: JSON.stringify({
        businessName: values.businessName,
        industry: values.industry,
        countryCode: country.code,
        currency: country.currency,
        timezone: country.timezone,
        ownerFirstName: values.ownerFirstName,
        ownerLastName: values.ownerLastName,
        email: values.email,
        password,
      }),
    });
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.problem.code === "EMAIL_TAKEN") return { fieldErrors: { email: e.message }, values };
      if (e.problem.errors?.length) {
        return {
          fieldErrors: Object.fromEntries(e.problem.errors.map((x) => [x.field, x.message])),
          values,
        };
      }
      return { error: e.message, values };
    }
    return { error: "Cannot reach the server. Please try again.", values };
  }
  await setSession(result.tokens);
  redirect("/dashboard");
}

export async function logoutAction() {
  const refreshToken = await getRefreshToken();
  if (refreshToken) {
    await apiFetch("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) }).catch(() => undefined);
  }
  await clearSession();
  redirect("/login");
}
