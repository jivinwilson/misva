import { NextRequest, NextResponse } from "next/server";
import { apiFetch, type TokenPair } from "@/lib/api";
import { clearSession, getRefreshToken, setSession } from "@/lib/session";

function safeNext(value: string | null) {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
}

export async function GET(request: NextRequest) {
  const next = safeNext(request.nextUrl.searchParams.get("next"));
  const refreshToken = await getRefreshToken();
  if (refreshToken) {
    try {
      const tokens = await apiFetch<TokenPair>("/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      });
      await setSession(tokens);
      return NextResponse.redirect(new URL(next, request.url));
    } catch {
      // fall through to sign-in
    }
  }
  await clearSession();
  return NextResponse.redirect(new URL("/login", request.url));
}
