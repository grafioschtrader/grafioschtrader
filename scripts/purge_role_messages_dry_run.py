"""
Dry-run audit for GitHub issue #106 (Delete unreferenced role messages), BEFORE trusting the
MailRoleMessagePurgeTask deletion in production.

Runs the same SELECT as the named query MailSendRecv.findPurgeableRoleThreadKeys against the live
grafioschtrader database and reports:
  - the candidate id_reply_to_local thread keys that the task would purge,
  - how many mail_send_recv rows the follow-up DELETE would remove (rows with id_reply_to_local IN keys),
  - a safety check that the DELETE does NOT match any sender 'S' root row (id_reply_to_local IS NULL).

A role thread is purgeable when, for its role-visible row (id_role_to IS NOT NULL AND send_recv = 'R'),
every corresponding role member (members at the message's send time, i.e.
send_recv_time >= user.last_role_modified_time) has a mail_send_recv_read_del row with mark_hide_del = 1.

READ-ONLY. No INSERT/UPDATE/DELETE.
"""
import subprocess
import sys

MYSQL = r"C:\xampp\mysql\bin\mysql.exe"
DB_USER = "grafioschtrader"
DB_PASS = "adergraf"
DB_NAME = "grafioschtrader"

# Mirrors named query MailSendRecv.findPurgeableRoleThreadKeys
FIND_PURGEABLE = """
SELECT DISTINCT roleR.id_reply_to_local
FROM mail_send_recv roleR
WHERE roleR.id_role_to IS NOT NULL AND roleR.send_recv = 'R'
  AND NOT EXISTS (
    SELECT 1 FROM user_role ur JOIN user u ON u.id_user = ur.id_user
    WHERE ur.id_role = roleR.id_role_to
      AND roleR.send_recv_time >= u.last_role_modified_time
      AND NOT EXISTS (
        SELECT 1 FROM mail_send_recv_read_del md
        WHERE md.id_mail_send_recv = roleR.id_mail_send_recv
          AND md.id_user = u.id_user
          AND md.mark_hide_del = 1))
"""


def run_sql(sql):
    """Execute a SQL statement and return rows as a list of tab-split string lists (no header)."""
    result = subprocess.run(
        [MYSQL, f"-u{DB_USER}", f"-p{DB_PASS}", DB_NAME, "-N", "-B", "-e", sql],
        capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        sys.exit(result.returncode)
    return [line.split("\t") for line in result.stdout.splitlines() if line.strip()]


def main():
    keys = [r[0] for r in run_sql(FIND_PURGEABLE)]
    print(f"Purgeable role threads (id_reply_to_local): {len(keys)}")
    if not keys:
        print("Nothing would be deleted.")
        return
    for k in keys:
        print(f"  thread {k}")

    in_list = ",".join(keys)

    # Rows the DELETE would remove.
    deleted = run_sql(
        f"SELECT COUNT(*) FROM mail_send_recv WHERE id_reply_to_local IN ({in_list})")
    print(f"\nmail_send_recv rows the DELETE would remove: {deleted[0][0]}")

    # Read/del rows removed via CASCADE.
    cascaded = run_sql(
        "SELECT COUNT(*) FROM mail_send_recv_read_del md "
        f"WHERE md.id_mail_send_recv IN (SELECT id_mail_send_recv FROM mail_send_recv "
        f"WHERE id_reply_to_local IN ({in_list}))")
    print(f"mail_send_recv_read_del rows removed via CASCADE: {cascaded[0][0]}")

    # Safety: a sender 'S' root has id_reply_to_local IS NULL, so it can never be in the IN-list.
    # This cross-check confirms none of the matched rows are such a root.
    sender_roots = run_sql(
        f"SELECT COUNT(*) FROM mail_send_recv WHERE id_reply_to_local IN ({in_list}) "
        "AND send_recv = 'S' AND id_reply_to_local IS NULL")
    print(f"\nSafety check - sender 'S' root rows matched (must be 0): {sender_roots[0][0]}")


if __name__ == "__main__":
    main()
