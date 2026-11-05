import { logoutAction } from "@/app/actions/auth";
import { DashboardNav } from "@/components/dashboard-nav";

export default function DashboardLayout({ children }: LayoutProps<"/dashboard">) {
  return (
    <div className="min-h-screen bg-ivory">
      <header className="flex flex-wrap items-center justify-between gap-3 bg-obsidian px-6 py-3 text-champagne">
        <div className="flex items-center gap-8">
          <span className="font-serif text-xl tracking-wide text-ivory">MISEVA</span>
          <DashboardNav />
        </div>
        <form action={logoutAction}>
          <button
            type="submit"
            className="rounded-lg border border-gold/50 px-4 py-1.5 text-sm text-champagne transition hover:bg-emerald focus:outline-none focus-visible:ring-2 focus-visible:ring-gold"
          >
            Sign out
          </button>
        </form>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-10">{children}</main>
    </div>
  );
}
