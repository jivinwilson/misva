import Link from "next/link";
import { Suspense } from "react";
import { getHealth, type HealthResponse } from "@/lib/api";

async function loadHealth(): Promise<HealthResponse | null> {
  try {
    return await getHealth();
  } catch {
    return null;
  }
}

function Badge({ up, label }: { up: boolean; label: string }) {
  return (
    <div
      role="status"
      className="mt-12 inline-flex items-center gap-3 rounded-full border border-gold/40 bg-emerald/40 px-5 py-2 text-sm"
    >
      <span className={`h-2.5 w-2.5 rounded-full ${up ? "bg-gold" : "bg-red-400"}`} aria-hidden />
      {label}
    </div>
  );
}

async function ApiStatus() {
  const health = await loadHealth();
  const up = health?.status === "UP";
  return <Badge up={up} label={up ? `API online · ${health?.env}` : "API unreachable"} />;
}

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center bg-obsidian px-6 text-center text-champagne">
      <p className="mb-6 text-sm uppercase tracking-[0.4em] text-gold">Business Operations</p>
      <h1 className="font-serif text-6xl tracking-tight text-ivory sm:text-7xl">MISEVA</h1>
      <p className="mt-4 max-w-md text-lg text-champagne/80">
        Everything in Place. Every Service in Control.
      </p>
      <nav className="mt-10 flex flex-wrap justify-center gap-4">
        <Link
          href="/register"
          className="rounded-lg bg-gold px-6 py-3 font-medium text-obsidian transition hover:bg-champagne focus:outline-none focus-visible:ring-2 focus-visible:ring-ivory"
        >
          Create your business
        </Link>
        <Link
          href="/login"
          className="rounded-lg border border-gold/50 px-6 py-3 font-medium text-champagne transition hover:bg-emerald/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-gold"
        >
          Sign in
        </Link>
      </nav>
      <Suspense fallback={<Badge up={false} label="Checking API…" />}>
        <ApiStatus />
      </Suspense>
    </main>
  );
}
