import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const ACCESS_COOKIE = "miseva_at";
const REFRESH_COOKIE = "miseva_rt";

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasAccess = request.cookies.has(ACCESS_COOKIE);
  const hasRefresh = request.cookies.has(REFRESH_COOKIE);

  if (pathname.startsWith("/dashboard")) {
    if (hasAccess) return NextResponse.next();
    const target = hasRefresh
      ? `/auth/refresh?next=${encodeURIComponent(pathname + search)}`
      : "/login";
    return NextResponse.redirect(new URL(target, request.url));
  }

  if (hasAccess) return NextResponse.redirect(new URL("/dashboard", request.url));
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register"],
};
