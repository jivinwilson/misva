import "server-only";
import { cookies } from "next/headers";
import type { TokenPair } from "./api";

export const ACCESS_COOKIE = "miseva_at";
export const REFRESH_COOKIE = "miseva_rt";

const REFRESH_MAX_AGE = 60 * 60 * 24 * 30;

const base = {
  httpOnly: true,
  sameSite: "lax" as const,
  secure: process.env.NODE_ENV === "production",
  path: "/",
};

export async function setSession(tokens: TokenPair) {
  const jar = await cookies();
  jar.set(ACCESS_COOKIE, tokens.accessToken, { ...base, maxAge: tokens.expiresInSeconds - 10 });
  jar.set(REFRESH_COOKIE, tokens.refreshToken, { ...base, maxAge: REFRESH_MAX_AGE });
}

export async function clearSession() {
  const jar = await cookies();
  jar.delete(ACCESS_COOKIE);
  jar.delete(REFRESH_COOKIE);
}

export async function getAccessToken() {
  return (await cookies()).get(ACCESS_COOKIE)?.value;
}

export async function getRefreshToken() {
  return (await cookies()).get(REFRESH_COOKIE)?.value;
}
