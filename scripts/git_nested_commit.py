import os, subprocess, sys, json

env = dict(os.environ)
env['GIT_OPTIONAL_LOCKS'] = '0'
env['GIT_CONFIG_NOSYSTEM'] = '1'

cwd = r'C:\Code\zhouqiaor-AGenUI'
msg = sys.argv[1] if len(sys.argv) > 1 else 'commit'
files = sys.argv[2:]

# Step 1: Hash all files
blobs = {}  # path -> sha
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
    blobs[f] = r.stdout.strip()
    print(f"HASHED: {f} -> {blobs[f][:8]}")

if not blobs:
    sys.exit(1)

# Step 2: Build nested trees from the bottom up
# Group files by their parent directory
def build_tree(entries):
    """Build a tree from a dict of path->sha, handling nested directories."""
    # Group by first path component
    groups = {}
    direct = {}
    for path, sha in entries.items():
        parts = path.split('/', 1)
        if len(parts) == 1:
            direct[parts[0]] = sha
        else:
            head, rest = parts
            if head not in groups:
                groups[head] = {}
            groups[head][rest] = sha

    # Build subtrees first
    tree_entries = []
    for name, sha in direct.items():
        tree_entries.append(f"100644 blob {sha}\t{name}")

    for dirname, sub_entries in groups.items():
        subtree_sha = build_tree(sub_entries)
        tree_entries.append(f"040000 tree {subtree_sha}\t{dirname}")

    # Create this tree
    input_str = '\n'.join(sorted(tree_entries)) + '\n'
    r = subprocess.run(
        ['git', 'mktree'],
        input=input_str,
        capture_output=True, text=True, env=env, cwd=cwd
    )
    if r.returncode != 0:
        print(f"MKTREE FAIL for {list(direct.keys()) + list(groups.keys())}: {r.stderr}")
        print(f"INPUT: {input_str}")
        sys.exit(1)
    return r.stdout.strip()

root_tree = build_tree(blobs)
print(f"Root tree: {root_tree}")

# Step 3: Get parent
head_ref = os.path.join(cwd, '.git', 'refs', 'heads', 'glance-evolution')
parent = None
if os.path.exists(head_ref):
    with open(head_ref) as f:
        parent = f.read().strip()
    if not os.path.exists(os.path.join(cwd, '.git', 'objects', parent[:2], parent[2:])):
        parent = None  # parent object doesn't exist

# Step 4: Create commit
args = ['git', 'commit-tree', root_tree, '-m', msg]
if parent:
    args.extend(['-p', parent])
r = subprocess.run(args, capture_output=True, text=True, env=env, cwd=cwd)
if r.returncode != 0:
    print(f"COMMIT FAIL: {r.stderr}")
    sys.exit(1)
commit_sha = r.stdout.strip()
print(f"Commit: {commit_sha}")

# Step 5: Update ref
with open(head_ref, 'w') as f:
    f.write(commit_sha + '\n')
print(f"Ref updated: {commit_sha[:12]}")
