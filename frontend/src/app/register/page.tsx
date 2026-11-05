import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth-shell";
import { RegisterForm } from "./register-form";

export const metadata: Metadata = { title: "Create your business · MISEVA" };

export default function RegisterPage() {
  return (
    <AuthShell
      title="Create your business"
      subtitle="Set up MISEVA for your business. Free 14-day trial."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-emerald underline underline-offset-4">
            Sign in
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AuthShell>
  );
}
