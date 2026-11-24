#!/bin/sh
# rdf-array: refresh the copy of rdf-tests-contrib (rdf/, sparql/), as testing/rdf-tests-cg holds a
# copy of the W3C suites.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --depth 1 --branch rdf-undef https://github.com/rdf-array/rdf-tests-contrib.git "$tmp/rdf-tests-contrib"
git -C "$tmp/rdf-tests-contrib" rev-parse HEAD > "$here/rdf-tests-contrib.commit"
rm -rf "$here/rdf-tests-contrib"
mkdir -p "$here/rdf-tests-contrib"
cp -R "$tmp/rdf-tests-contrib/rdf" "$tmp/rdf-tests-contrib/sparql" "$here/rdf-tests-contrib/"
