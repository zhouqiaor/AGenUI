import os, subprocess, sys

env = dict(os.environ)
env['GIT_OPTIONAL_LOCKS'] = '0'
env['GIT_CONFIG_NOSYSTEM'] = '1'

cwd = r'C:\Code\zhouqiaor-AGenUI'
msg = sys.argv[1] if len(sys.argv) > 1 else 'commit'
# Remaining args are file paths relative to cwd
files = sys.argv[2:]

# Build tree entries manually
entries = []
for f in files:
    full = os.path.join(cwd, f.replace('/', os.sep))
    if not os.path.exists(full):
        print(f"SKIP: {f}")
        continue
    r = subprocess.run(
        ['git', 'hash-object', '-w', '--', full],
        capture_output=True, text=True, env=env, cwd=cwd
    )
    if r.returncode != 0:
        print(f"HASH FAIL {f}: {r.stderr}")
        continue
    sha = r.stdout.strip()
    # Format: "100644 <sha> <path>"
    entries.append(f"100644 blob {sha}\t{f}")
    print(f"HASHED: {f} -> {sha[:8]}")

if not entries:
    print("No files to commit")
    sys.exit(1)

# Create tree using mktree (doesn't need index.lock)
import subprocess as sp
r = sp.run(
    ['git', 'mktree'],
    input='\n'.join(entries) + '\n',
    capture_output=True, text=True, env=env, cwd=cwd
)
if r.returncode != 0:
    print(f"MKTREE FAIL: {r.stderr}")
    sys.exit(1)
tree_sha = r.stdout.strip()
print(f"Tree: {tree_sha}")

# Get parent
head_ref = os.path.join(cwd, '.git', 'refs', 'heads', 'glance-evolution')
parent = None
if os.path.exists(head_ref):
    with open(head_ref) as f:
        parent = f.read().strip()

# Create commit
args = ['git', 'commit-tree', tree_sha, '-m', msg]
if parent:
    args.extend(['-p', parent])
r = sp.run(args, capture_output=True, text=True, env=env, cwd=cwd)
if r.returncode != 0:
    print(f"COMMIT FAIL: {r.stderr}")
    sys.exit(1)
commit_sha = r.stdout.strip()
print(f"Commit: {commit_sha}")

# Update ref
with open(head_ref, 'w') as f:
    f.write(commit_sha + '\n')
print(f"Done: ref -> {commit_sha[:12]}")
