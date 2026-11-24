/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    // 127.0.0.1 et non « localhost » : Node peut résoudre localhost en ::1 (IPv6), et un autre
    // service publié sur [::]:8091 (ex. un conteneur Docker) recevrait alors les appels de l'API.
    const api = process.env.NEXT_PUBLIC_API_BASE || "http://127.0.0.1:8091";
    return [
      { source: "/api/:path*", destination: `${api}/api/:path*` },
    ];
  },
};

export default nextConfig;
