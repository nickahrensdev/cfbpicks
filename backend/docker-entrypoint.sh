#!/bin/sh
# Temporary network diagnostic. Every SSL handshake to the Supabase pooler
# from this environment has died the same way after ~45-60s regardless of
# any JVM/driver flag - which points at something between here and the
# pooler, not the JDBC config. This probes the TLS layer directly with
# openssl before the app starts, so the deploy log shows what actually
# happens on the wire rather than only Java's already-mangled exception.
#
# Non-fatal either way: whatever this prints, the real app still starts.
# -starttls postgres replicates what pgjdbc actually does: send the
# Postgres SSLRequest preamble, read the server's 'S'/'N' reply, then
# negotiate TLS on the same socket. A raw TLS probe skips that preamble and
# fails differently regardless of whether the real path works.
echo "=== TLS probe: $SUPABASE_POOLER_HOST:5432 ==="
timeout 20 openssl s_client -starttls postgres -connect "$SUPABASE_POOLER_HOST:5432" -brief </dev/null 2>&1 | tail -30
echo "=== TLS probe: $SUPABASE_POOLER_HOST:6543 ==="
timeout 20 openssl s_client -starttls postgres -connect "$SUPABASE_POOLER_HOST:6543" -brief </dev/null 2>&1 | tail -30
echo "=== end probe ==="

exec java -jar app.jar
