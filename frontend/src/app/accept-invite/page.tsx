import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AuthShell } from "@/components/auth-shell";
import { ApiError, apiFetch, type InvitationPreview } from "@/lib/api";
import { AcceptForm } from "./accept-form";

export const metadata: Metadata = { title: "Accept invitation · MISEVA" };

export default function AcceptInvitePage({ searchParams }: PageProps<"/accept-invite">) {
  return (
    <Suspense fallback={<AuthShell title="Checking your invitation…" subtitle="One moment." footer={null}>{null}</AuthShell>}>
      <Invitation searchParams={searchParams} />
    </Suspense>
  );
}

async function Invitation({ searchParams }: { searchParams: PageProps<"/accept-invite">["searchParams"] }) {
  const raw = (await searchParams).token;
  const token = Array.isArray(raw) ? raw[0] : raw;

  let preview: InvitationPreview | null = null;
  if (token) {
    try {
      preview = await apiFetch<InvitationPreview>(`/invitations/preview?token=${encodeURIComponent(token)}`);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
    }
  }

  if (!token || !preview) {
    return (
      <AuthShell
        title="This invitation is not valid"
        subtitle="It may have expired, been used already or been cancelled. Ask your administrator to send a new one."
        footer={
          <Link href="/login" className="font-medium text-emerald underline underline-offset-4">
            Go to sign in
          </Link>
        }
      >
        {null}
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title={`Join ${preview.businessName}`}
      subtitle={
        preview.existingAccount
          ? `Hi ${preview.firstName ?? "there"}, confirm your MISEVA password to accept this invitation.`
          : `Hi ${preview.firstName ?? "there"}, choose a password to finish setting up your account.`
      }
      footer={<>Invitation for {preview.email}</>}
    >
      <AcceptForm token={token} existingAccount={preview.existingAccount} />
    </AuthShell>
  );
}
