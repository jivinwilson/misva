import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { ApiError, apiFetch, type BranchList, type BusinessSummary, type Me } from "@/lib/api";
import { industryLabel } from "@/lib/reference-data";
import { getAccessToken } from "@/lib/session";

export const metadata: Metadata = { title: "Dashboard · MISEVA" };

export default function DashboardPage() {
  return (
    <Suspense fallback={<p className="text-obsidian/60">Loading your workspace…</p>}>
      <Workspace />
    </Suspense>
  );
}

async function Workspace() {
  const token = await getAccessToken();
  if (!token) redirect("/auth/refresh?next=/dashboard");

  let me: Me, business: BusinessSummary, branches: BranchList;
  try {
    [me, business, branches] = await Promise.all([
      apiFetch<Me>("/me", { token }),
      apiFetch<BusinessSummary>("/businesses/current", { token }),
      apiFetch<BranchList>("/branches", { token }),
    ]);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard");
    return (
      <div role="alert" className="rounded-lg border border-red-700/30 bg-red-50 px-4 py-3 text-red-800">
        Could not load your workspace. Please try again.
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <section>
        <p className="text-xs uppercase tracking-[0.3em] text-gold">{industryLabel(business.industry)}</p>
        <h1 className="mt-2 font-serif text-4xl text-obsidian">{business.name}</h1>
        <p className="mt-2 text-obsidian/70">
          {business.country} · {business.currency} · {business.timezone}
        </p>
      </section>

      <section aria-labelledby="branches" className="rounded-2xl border border-obsidian/10 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <h2 id="branches" className="font-serif text-xl text-obsidian">Branches</h2>
          <Link href="/dashboard/branches" className="text-sm font-medium text-emerald underline underline-offset-4">
            Manage
          </Link>
        </div>
        <ul className="mt-4 divide-y divide-obsidian/10">
          {branches.items.map((b) => (
            <li key={b.id} className="flex items-center justify-between py-3">
              <span>
                <span className="font-medium text-obsidian">{b.name}</span>
                <span className="ml-3 text-sm text-obsidian/60">{b.code}</span>
              </span>
              {b.headOffice ? (
                <span className="rounded-full bg-champagne px-3 py-1 text-xs font-medium text-emerald">Head office</span>
              ) : null}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="access" className="rounded-2xl border border-obsidian/10 bg-white p-6 shadow-sm">
        <h2 id="access" className="font-serif text-xl text-obsidian">Your access</h2>
        <ul className="mt-4 flex flex-wrap gap-2">
          {[...me.permissions].sort().map((p) => (
            <li key={p} className="rounded-full bg-emerald/10 px-3 py-1 text-xs text-emerald">{p}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
