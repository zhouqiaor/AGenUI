import os, subprocess, time, sys

lock = r'C:\Code\zhouqiaor-AGenUI\.git\index.lock'
for i in range(20):
    try:
        if os.path.exists(lock):
            os.remove(lock)
    except Exception:
        pass
    time.sleep(0.01)

env = dict(os.environ)
env['GIT_OPTIONAL_LOCKS'] = '0'
env['GIT_CONFIG_NOSYSTEM'] = '1'

msg = sys.argv[1] if len(sys.argv) > 1 else 'commit'
r = subprocess.run(
    ['git', '-c', 'gc.auto=0', 'commit', '--allow-empty', '-m', msg],
    capture_output=True, text=True, env=env,
    cwd=r'C:\Code\zhouqiaor-AGenUI'
)
print('OUT:', r.stdout)
print('ERR:', r.stderr)
print('RC:', r.returncode)
