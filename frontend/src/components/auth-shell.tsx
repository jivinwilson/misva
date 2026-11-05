import Link from "next/link";
import type { ReactNode } from "react";

export function AuthShell({ title, subtitle, children, footer }: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <main className="grid min-h-screen flex-1 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-obsidian p-12 text-champagne lg:flex">
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-40 -left-32 h-[34rem] w-[34rem] rounded-full bg-emerald/70 blur-3xl"
        />
        <Link href="/" className="relative font-serif text-2xl tracking-wide text-ivory">
          MISEVA
        </Link>
        <div className="relative max-w-sm">
          <p className="mb-4 text-xs uppercase tracking-[0.35em] text-gold">Business operations</p>
          <p className="font-serif text-4xl leading-tight text-ivory">Everything in Place. Every Service in Control.</p>
          <p className="mt-6 text-champagne/70">
            One platform for every branch, every team and every service you run.
          </p>
        </div>
        <p className="relative text-xs text-champagne/50">© MISEVA</p>
      </aside>

      <section className="flex flex-col justify-center bg-ivory px-6 py-12 sm:px-12">
        <div className="mx-auto w-full max-w-md">
          <Link href="/" className="mb-10 block font-serif text-xl tracking-wide text-emerald lg:hidden">
            MISEVA
          </Link>
          <h1 className="font-serif text-3xl text-obsidian">{title}</h1>
          <p className="mt-2 text-obsidian/70">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <p className="mt-8 text-sm text-obsidian/70">{footer}</p>
        </div>
      </section>
    </main>
  );
}
