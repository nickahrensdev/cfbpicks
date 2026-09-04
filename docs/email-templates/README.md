# Email templates

Supabase renders these; they are pasted into
**Authentication → Emails** in the dashboard. They live here because a
template that exists only in a dashboard is config nobody can review, diff or
restore — and this one carries a URL that has already broken twice.

## Why they do not use `{{ .ConfirmationURL }}`

That variable resolves to Supabase's own `/auth/v1/verify` endpoint, which
decides where to send the reader afterwards. That decision runs through Site
URL and the redirect allow-list, and when it falls back it keeps only the
*origin* of Site URL — dropping the `/cfbpicks` base path this app is served
under and landing every confirmed member on a GitHub 404.

So the link points at the app instead, carrying the token hash, and
`ConfirmEmailPage` exchanges it for a session with `verifyOtp`.

## No paste-able fallback URL

Deliberate. The raw link carries a single-use credential, and printing it in
the body invites it into screenshots and forwarded mail. The button is the
only way in.

## The trailing slash matters

The links read `{{ .SiteURL }}confirm?…` with no slash of their own, because
Site URL already ends in one. If Site URL is ever saved without it, these
templates produce `…/cfbpicksconfirm` and every link breaks. The two are
coupled; change one and check the other.

## Editing

Paste the file's whole contents into the matching template's Body field.
Supabase has no import, so the copy in the dashboard and the copy here are
kept in step by hand — update both.
