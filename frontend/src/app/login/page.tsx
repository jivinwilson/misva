import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth-shell";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Sign in · MISEVA" };

export default function LoginPage() {
  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in to manage your business."
      footer={
        <>
          New to MISEVA?{" "}
          <Link href="/register" className="font-medium text-emerald underline underline-offset-4">
            Create your business
          </Link>
        </>
      }
    >
      <LoginForm />
    </AuthShell>
  );
}
