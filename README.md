# ブレインロット対策
short動画を検知して警告するアプリ

# 開発手順
dockerをインストール！
https://qiita.com/honda-dev-jp/items/a372b11f47052dc20e8a

Dockerを開きながら、shortblockerディレクトリに移動して以下を実行！
sudo docker compose -f docker-compose.yml up --build

以下で閲覧可能！
http://localhost:3000/

画像は一旦frontend/imagesに格納してるよ！


#　以下AIによるGitHub解説
cloneで取り込む方法
以下でローカルに取り込める！
git clone https://github.com/sumin-creator/shortblocker.git
cd shortblocker

ブランチ作成の例
基本はmainで直接作業せず、ブランチを切って作業するのがおすすめ！
ブランチ名は何でもOK（例: feature/top-page, fix/readme など）

新しい作業ブランチを作る
git switch -c feature/update-top-page

変更をコミットしてpushする
git add .
git commit -m "トップページの見た目を調整"
git push -u origin feature/update-top-page

mainを最新にしてから新しくブランチを切る例
git switch main
git pull origin main
git switch -c feature/another-task

mainの更新方法
作業ブランチで変更したあと、mainに反映して更新する流れは以下！

1) GitHubでPull Requestを作成してmainにマージ
2) ローカルのmainを更新
git switch main
git pull origin main

3) 次の作業は新しいブランチで開始
git switch -c feature/next-task