# Sourced by run.sh, deploy.sh and open.sh: resolves the signing team into $TEAM
# and saves it to the untracked Team.xcconfig, which Signing.xcconfig includes, so
# every generated project (and Xcode's Signing & Capabilities) carries the team.
#
# In order of preference: an explicit env var, an untracked mobile/ios/.team file,
# else the team of the first keychain identity matching $1 (an extended regex).
# Kept out of project.yml so the committed spec stays team-agnostic.
TEAM="${DEVELOPMENT_TEAM:-$(cat .team 2>/dev/null || true)}"
if [ -z "$TEAM" ]; then
  # The ID in the identity's name is the person's, not the team's; the team is
  # the certificate's OU.
  IDENTITY=$(security find-identity -v -p codesigning \
    | sed -nE "s/.*\"($1: [^\"]+)\".*/\1/p" | head -1)
  if [ -n "$IDENTITY" ]; then
    TEAM=$(security find-certificate -c "$IDENTITY" -p | openssl x509 -noout -subject \
      | sed -nE 's/.*OU ?= ?([A-Z0-9]{10}).*/\1/p' || true)
  fi
fi
if [ -z "$TEAM" ]; then
  echo "No signing team found. Set one with DEVELOPMENT_TEAM=XXXXXXXXXX $0" >&2
  echo "or write it to mobile/ios/.team (find it in Xcode → Signing & Capabilities → Team)." >&2
  exit 1
fi

echo "DEVELOPMENT_TEAM = $TEAM" > Team.xcconfig
