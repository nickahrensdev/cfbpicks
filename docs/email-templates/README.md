# Email templates

Supabase renders these; they are pasted into
**Authentication → Emails** in the dashboard. They live here because a
template that exists only in a dashboard is config nobody can review, diff or
restore — and these carry a URL that has already broken twice.

| File | Dashboard template | Lands on |
| --- | --- | --- |
| `confirm-signup.html` | Confirm signup | `/confirm` |
| `reset-password.html` | Reset password | `/reset-password` |

## Why they do not use `{{ .ConfirmationURL }}`

That variable resolves to Supabase's own `/auth/v1/verify` endpoint, which
decides where to send the reader afterwards. That decision runs through Site
URL and the redirect allow-list, and when it falls back it keeps only the
*origin* of Site URL — dropping the `/cfbpicks` base path this app is served
under and landing every confirmed member on a GitHub 404.

So the link points at the app instead, carrying the token hash, and
`ConfirmEmailPage` / `ResetPasswordPage` exchange it for a session with
`verifyOtp`.

For the reset link that exchange is also what authorises the change: the person
resetting a password does not have the old one, so holding a live link from
their inbox is the proof of identity. That is why `/reset-password` is
unguarded, and why it asks for no current password.

## No paste-able fallback URL

Deliberate. The raw link carries a single-use credential, and printing it in
the body invites it into screenshots and forwarded mail. The button is the
only way in.

## Expiry

Both footers say 24 hours because that is Supabase's default email link
lifetime (`MAILER_OTP_EXP`, 86400s). It is **one setting covering both
templates**, so the two must claim the same number — and the sign-in page
repeats it when it says a reset link has been sent, which is a third copy to
keep in step. If the setting is changed, change all three: a link that outlives
what the email claims is merely confusing, but one that dies sooner sends
people to a dead end believing they have time.

Someone whose link has expired can send themselves another from the sign-in
form — "Forgot your password?" for a reset, and signing in for a confirmation.
There is no need to sign up again in either case.

## The trailing slash matters

The links read `{{ .SiteURL }}confirm?…` and `{{ .SiteURL }}reset-password?…`
with no slash of their own, because Site URL already ends in one. If Site URL is
ever saved without it, these templates produce `…/cfbpicksconfirm` and every
link breaks. The two are coupled; change one and check the other.

## Redirect URLs

`/reset-password` must be in **Authentication → URL Configuration → Redirect
URLs**, because `resetPasswordForEmail` passes it as `redirectTo` and Supabase
rejects a destination that is not on the allow-list.

## Editing

Paste the file's whole contents into the matching template's Body field.
Supabase has no import, so the copy in the dashboard and the copy here are
kept in step by hand — update both.
