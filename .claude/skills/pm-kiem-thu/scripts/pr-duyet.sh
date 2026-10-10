#!/usr/bin/env bash
# PM: mỗi PR mở (số >= $1, mặc định 182) in head đầy đủ, mergeable_state, CI bắt buộc theo head,
# kết luận mới nhất của Luna 3 (review) và Luna Ultra/Haiku (comment). Dùng: pr-duyet.sh [từ_số]
R=repos/aowvn-10diem/aowvn-monika; FROM=${1:-182}
for p in $(gh api "$R/pulls?state=open&per_page=30" --jq ".[]|select(.number>=$FROM)|.number"); do
  read -r ref sha st < <(gh api $R/pulls/$p --jq '"\(.head.ref) \(.head.sha) \(.mergeable_state)"')
  ci=$(gh api "$R/commits/$sha/check-runs?per_page=50" --jq '[.check_runs[]|select(.name|test("^(build|coverage|n04-tests|analyze)"))|"\(.name|.[0:12])=\(.conclusion//.status)"]|unique|join(",")')
  echo "#$p $ref $sha $st | $ci"
  gh api $R/pulls/$p/reviews --jq '.[-1:][]|"  R \(.commit_id[0:7]) \(.body|split("\n")[0][0:24]) => \(.body|capture("Kết luận:\\s*(?<k>[^\n]{0,50})")?.k // "?")"'
  gh api "$R/issues/$p/comments" --jq '[.[]|select(.body|test("tiền duyệt|lần hai"))][-1:][]|"  C \(.body|split("\n")[0][0:40]) => \(.body|capture("Kết luận:\\s*(?<k>[^\n]{0,50})")?.k // "?")"'
done
# Lần đẩy gần nhất theo người (nhánh sol/ nova/ haiku2/ luna3/ luna-ultra/): biết ai đang làm dù chưa mở PR.
git fetch -q origin '+refs/heads/*:refs/remotes/origin/*' 2>/dev/null
for a in sol nova haiku2 luna3 luna-ultra; do
  git for-each-ref --sort=-committerdate --count=1 --format="  đẩy cuối $a: %(committerdate:iso8601) %(refname:lstrip=3) %(objectname:short)" "refs/remotes/origin/$a/"
done
