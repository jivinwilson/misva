import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { setBranchActiveAction } from "@/app/actions/branches";
import { BranchDialog } from "@/components/branch-dialog";
import { ApiError, apiFetch, type BranchList, type Me } from "@/lib/api";
import { getAccessToken } from "@/lib/session";

export const metadata: Metadata = { title: "Branches · MISEVA" };

const primaryButton =
  "rounded-lg bg-obsidian px-4 py-2 text-sm font-medium text-ivory transition hover:bg-emerald focus:outline-none focus-visible:ring-2 focus-visible:ring-gold focus-visible:ring-offset-2";
const subtleButton =
  "rounded-lg border border-obsidian/20 px-3 py-1.5 text-sm text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-gold";

export default function BranchesPage() {
  return (
    <Suspense fallback={<p className="text-obsidian/60">Loading branches…</p>}>
      <Branches />
    </Suspense>
  );
}

async function Branches() {
  const token = await getAccessToken();
  if (!token) redirect("/auth/refresh?next=/dashboard/branches");

  let me: Me, list: BranchList;
  try {
    [me, list] = await Promise.all([apiFetch<Me>("/me", { token }), apiFetch<BranchList>("/branches", { token })]);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/branches");
    return (
      <div role="alert" className="rounded-lg border border-red-700/30 bg-red-50 px-4 py-3 text-red-800">
        Could not load branches. Please try again.
      </div>
    );
  }

  const can = (p: string) => me.permissions.includes(p);
  const atLimit = list.maxBranches !== undefined && list.used >= list.maxBranches;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl text-obsidian">Branches</h1>
          <p className="mt-1 text-obsidian/70">
            {list.used} {list.used === 1 ? "branch" : "branches"}
            {list.maxBranches !== undefined ? ` of ${list.maxBranches} on your plan` : " · unlimited on your plan"}
          </p>
        </div>
        {can("business.branch.create") ? (
          atLimit ? (
            <p className="rounded-lg bg-champagne px-4 py-2 text-sm text-obsidian">
              Branch limit reached. Upgrade your plan to add more.
            </p>
          ) : (
            <BranchDialog triggerLabel="Add branch" triggerClassName={primaryButton} />
          )
        ) : null}
      </div>

      <ul className="divide-y divide-obsidian/10 rounded-2xl border border-obsidian/10 bg-white shadow-sm">
        {list.items.map((b) => (
          <li key={b.id} className="flex flex-wrap items-center justify-between gap-4 px-6 py-4">
            <div className="min-w-0">
              <p className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-obsidian">{b.name}</span>
                <span className="text-sm text-obsidian/60">{b.code}</span>
                {b.headOffice ? (
                  <span className="rounded-full bg-champagne px-2.5 py-0.5 text-xs font-medium text-emerald">Head office</span>
                ) : null}
                {b.status !== "active" ? (
                  <span className="rounded-full bg-obsidian/10 px-2.5 py-0.5 text-xs text-obsidian/70">Inactive</span>
                ) : null}
              </p>
              <p className="mt-1 truncate text-sm text-obsidian/60">
                {[b.addressLine1, b.city, b.region, b.countryCode].filter(Boolean).join(", ")}
              </p>
            </div>
            {can("business.branch.update") ? (
              <div className="flex items-center gap-2">
                <BranchDialog branch={b} triggerLabel="Edit" triggerClassName={subtleButton} />
                {b.headOffice ? null : (
                  <form action={setBranchActiveAction}>
                    <input type="hidden" name="id" value={b.id} />
                    <input type="hidden" name="active" value={String(b.status !== "active")} />
                    <button type="submit" className={subtleButton}>
                      {b.status === "active" ? "Deactivate" : "Activate"}
                    </button>
                  </form>
                )}
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
