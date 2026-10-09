#!/usr/bin/env bash
# Run on the owner's authenticated computer. Secret values never go to stdout.
set -euo pipefail
set +x
umask 077

repository='layland/MyExercise'
for tool in gh keytool python3; do
  command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo 'Authenticate GitHub CLI with gh auth login first.' >&2; exit 1; }
existing_secrets=$(gh secret list --repo "$repository" --json name --jq '.[].name')
if [[ "$existing_secrets" == *ANDROID_KEYSTORE_BASE64* || "$existing_secrets" == *ANDROID_KEYSTORE_PASSWORD* || "$existing_secrets" == *ANDROID_KEY_ALIAS* || "$existing_secrets" == *ANDROID_KEY_PASSWORD* ]]; then
  echo 'Existing Android signing Secrets found. Refusing to overwrite the app signing identity.' >&2
  exit 1
fi

signing_directory="${XDG_CONFIG_HOME:-$HOME/.config}/myexercise/signing"
mkdir -p "$signing_directory"
chmod 700 "$signing_directory"
export SIGNING_SETUP_DIRECTORY="$signing_directory"
python3 - <<'PY'
import os, secrets
from pathlib import Path
root = Path(os.environ['SIGNING_SETUP_DIRECTORY'])
for name in ('store-password', 'key-password'):
    path = root / name
    if not path.exists():
        with path.open('x') as stream:
            stream.write(secrets.token_urlsafe(32))
        path.chmod(0o600)
PY

key_alias='myexercise-release'
keystore="$signing_directory/release.jks"
if [[ ! -f "$keystore" ]]; then
  keytool -genkeypair -noprompt -storetype JKS -keystore "$keystore" \
    -storepass:file "$signing_directory/store-password" \
    -keypass:file "$signing_directory/key-password" \
    -alias "$key_alias" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=MyExercise, OU=Android, O=layland' >/dev/null 2>&1
fi
keytool -list -keystore "$keystore" -storepass:file "$signing_directory/store-password" -alias "$key_alias" >/dev/null

python3 - <<'PY' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repository"
import base64, os
from pathlib import Path
print(base64.b64encode((Path(os.environ['SIGNING_SETUP_DIRECTORY']) / 'release.jks').read_bytes()).decode())
PY
gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$repository" < "$signing_directory/store-password"
gh secret set ANDROID_KEY_PASSWORD --repo "$repository" < "$signing_directory/key-password"
printf '%s' "$key_alias" | gh secret set ANDROID_KEY_ALIAS --repo "$repository"

echo "Signing Secrets registered. Keep a secure backup of: $signing_directory"
run_id=$(gh run list --repo "$repository" --workflow android.yml --limit 1 --json databaseId --jq '.[0].databaseId')
if [[ -n "$run_id" ]]; then
  gh run rerun "$run_id" --failed --repo "$repository"
  gh run watch "$run_id" --repo "$repository" --exit-status
else
  gh workflow run android.yml --repo "$repository"
  echo 'Build requested. Check the repository Actions page for the signed APK.'
fi
