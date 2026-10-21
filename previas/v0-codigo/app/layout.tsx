import type { Metadata, Viewport } from 'next'
import { Atkinson_Hyperlegible } from 'next/font/google'
import './globals.css'

const _atkinson = Atkinson_Hyperlegible({
  subsets: ['latin'],
  weight: ['400', '700'],
})

export const metadata: Metadata = {
  title: 'Radar Corridas · Protótipo de telas',
  description:
    'Protótipo navegável do app Radar Corridas para motoristas de aplicativo: cartão da oferta, ganhos, mapa, bolinha flutuante e avisos, em tema escuro e claro.',
  generator: 'v0.app',
}

export const viewport: Viewport = {
  themeColor: '#0D1117',
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="pt-BR" className="dark bg-background">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..700,0..1,0&display=block"
        />
      </head>
      <body className="font-sans antialiased">{children}</body>
    </html>
  )
}
