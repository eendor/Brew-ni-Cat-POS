import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: "Brew ni Cat - POS Manager Panel",
  description: "Live management dashboard for Brew ni Cat POS",
};

// Set the saved theme before first paint so there's no light/dark flash. Defaults
// to the OS preference the first time, then follows whatever the owner picks.
const themeInitScript = `(function(){try{var t=localStorage.getItem('bnc-theme');if(!t){t=window.matchMedia&&window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';}document.documentElement.setAttribute('data-theme',t);}catch(e){document.documentElement.setAttribute('data-theme','dark');}})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body className={inter.variable}>{children}</body>
    </html>
  );
}
