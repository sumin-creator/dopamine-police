import "./globals.css";

export const metadata = {
  title: "Shortblocker",
  description: "Brainrot only page",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ja">
      <body>{children}</body>
    </html>
  );
}
