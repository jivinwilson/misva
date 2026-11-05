import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";
import { revokeInvitationAction } from "@/app/actions/employees";
import { InviteDialog } from "@/components/invite-dialog";
import { ResendInvitation } from "@/components/resend-invitation";
import { ApiError, apiFetch, type BranchList, type EmployeeView, type Me, type RoleView } from "@/lib/api";
import { getAccessToken } from "@/lib/session";

export const metadata: Metadata = { title: "Team · MISEVA" };

const primaryButton =
  "rounded-lg bg-obsidian px-4 py-2 text-sm font-medium text-ivory transition hover:bg-emerald focus:outline-none focus-visible:ring-2 focus-visible:ring-gold focus-visible:ring-offset-2";
const subtleButton =
  "rounded-lg border border-obsidian/20 px-3 py-1.5 text-sm text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-gold disabled:opacity-60";

export default function TeamPage() {
  return (
    <Suspense fallback={<p className="text-obsidian/60">Loading your team…</p>}>
      <Team />
    </Suspense>
  );
}

function daysLeft(iso?: string) {
  if (!iso) return "";
  const days = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  return days <= 0 ? "expired" : `expires in ${days} day${days === 1 ? "" : "s"}`;
}

async function Team() {
  const token = await getAccessToken();
  if (!token) redirect("/auth/refresh?next=/dashboard/team");

  let me: Me | undefined;
  let employees: EmployeeView[] = [];
  let roles: RoleView[] = [];
  let branches: BranchList | undefined;
  let failed = false;
  try {
    me = await apiFetch<Me>("/me", { token });
    if (me.permissions.includes("employee.member.read")) {
      [employees, branches] = await Promise.all([
        apiFetch<EmployeeView[]>("/employees", { token }),
        apiFetch<BranchList>("/branches", { token }),
      ]);
      if (me.permissions.includes("employee.member.invite")) {
        roles = await apiFetch<RoleView[]>("/roles/assignable", { token });
      }
    }
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) redirect("/auth/refresh?next=/dashboard/team");
    failed = true;
  }

  if (failed || !me) {
    return (
      <div role="alert" className="rounded-lg border border-red-700/30 bg-red-50 px-4 py-3 text-red-800">
        Could not load your team. Please try again.
      </div>
    );
  }
  if (!branches) {
    return (
      <div role="alert" className="rounded-lg border border-obsidian/15 bg-white px-4 py-3 text-obsidian/80">
        You do not have access to the team list.
      </div>
    );
  }

  const canInvite = me.permissions.includes("employee.member.invite");
  const canGrantAll = !branches.restricted;
  const name = (x: EmployeeView) => [x.firstName, x.lastName].filter(Boolean).join(" ") || x.email;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-serif text-3xl text-obsidian">Team</h1>
          <p className="mt-1 text-obsidian/70">
            {employees.filter((x) => x.status === "active").length} active ·{" "}
            {employees.filter((x) => x.status === "invited").length} invited
          </p>
        </div>
        {canInvite ? (
          <InviteDialog
            roles={roles}
            branches={branches.items.filter((b) => b.status === "active")}
            canGrantAllBranches={canGrantAll}
            triggerClassName={primaryButton}
          />
        ) : null}
      </div>

      <ul className="divide-y divide-obsidian/10 rounded-2xl border border-obsidian/10 bg-white shadow-sm">
        {employees.map((x) => (
          <li key={x.membershipId} className="flex flex-wrap items-start justify-between gap-4 px-6 py-4">
            <div className="min-w-0">
              <p className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-obsidian">{name(x)}</span>
                {x.employeeCode ? <span className="text-sm text-obsidian/60">{x.employeeCode}</span> : null}
                {x.status === "invited" ? (
                  <span className="rounded-full bg-champagne px-2.5 py-0.5 text-xs font-medium text-obsidian">
                    Invited · {daysLeft(x.invitationExpiresAt)}
                  </span>
                ) : null}
                {x.status === "suspended" ? (
                  <span className="rounded-full bg-obsidian/10 px-2.5 py-0.5 text-xs text-obsidian/70">Suspended</span>
                ) : null}
              </p>
              <p className="mt-1 truncate text-sm text-obsidian/60">
                {x.email}
                {x.jobTitle ? ` · ${x.jobTitle}` : ""}
              </p>
              <p className="mt-2 flex flex-wrap gap-2">
                {x.roles.map((r) => (
                  <span key={r} className="rounded-full bg-emerald/10 px-2.5 py-0.5 text-xs text-emerald">{r}</span>
                ))}
                <span className="rounded-full bg-obsidian/5 px-2.5 py-0.5 text-xs text-obsidian/70">
                  {x.allBranches ? "All branches" : x.branches.join(", ") || "No branches"}
                </span>
              </p>
            </div>
            {canInvite && x.status === "invited" ? (
              <div className="flex items-start gap-2">
                <ResendInvitation membershipId={x.membershipId} className={subtleButton} />
                <form action={revokeInvitationAction}>
                  <input type="hidden" name="id" value={x.membershipId} />
                  <button type="submit" className={subtleButton}>Revoke</button>
                </form>
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}

