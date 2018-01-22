#!/usr/bin/env sh

BLOOP_SPECIFIED_VERSION=$1
BLOOP_LATEST_RELEASE=no-tag-yet
NAILGUN_COMMIT=0927946db663927151a53fe3b365b2655613db86
BLOOP_INSTALLATION_TARGET="$HOME/.bloop"
BLOOP_SERVER_TARGET="${BLOOP_INSTALLATION_TARGET}/bloop-server"
BLOOP_SHELL_TARGET="${BLOOP_INSTALLATION_TARGET}/bloop-shell"
BLOOP_CLIENT_TARGET="${BLOOP_INSTALLATION_TARGET}/bloop-ng.py"

BLOOP_VERSION=${BLOOP_SPECIFIED_VERSION:-$BLOOP_LATEST_RELEASE}

test -e ~/.coursier/coursier || ( \
  mkdir -p ~/.coursier && \
  curl -Lso ~/.coursier/coursier https://git.io/vgvpD && \
  chmod +x ~/.coursier/coursier \
)

test -e "$BLOOP_SERVER_TARGET" || ( \
  mkdir -p ~/.bloop && \
  ~/.coursier/coursier bootstrap ch.epfl.scala:bloop-frontend_2.12:$BLOOP_VERSION \
    -o "$BLOOP_SERVER_TARGET" \
    --standalone \
    --main bloop.Server && \
  echo "Installed bloop server in '$BLOOP_SERVER_TARGET'"
)

test -e "$BLOOP_SHELL_TARGET" || ( \
  mkdir -p ~/.bloop && \
  ~/.coursier/coursier bootstrap ch.epfl.scala:bloop-frontend_2.12:$BLOOP_VERSION \
    -o "$BLOOP_SHELL_TARGET" \
    --standalone \
    --main bloop.Bloop && \
  echo "Installed bloop shell in '$BLOOP_SHELL_TARGET'"
)

test -e "$BLOOP_CLIENT_TARGET" || ( \
  mkdir -p ~/.bloop && \
  curl -Ls https://raw.githubusercontent.com/scalacenter/nailgun/$NAILGUN_COMMIT/pynailgun/ng.py \
    -o "$BLOOP_CLIENT_TARGET" && \
  chmod +x ~/.bloop/bloop-ng.py && \
  echo "Installed bloop client in '$BLOOP_CLIENT_TARGET'"
)

