import "./globals.css";

export const metadata = {
  title: "Shortblocker | Short-Video Intervention Prototype",
  description:
    "UIベースで短尺動画視聴状態を検知し、キャラクターが介入するショート動画対策アプリのWebプロトタイプ",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ja">
      <body>{children}</body>
    </html>
  );
}
