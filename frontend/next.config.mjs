/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  webpack: (config) => {
    // react-pdf requires canvas module which is not available in SSR
    config.resolve.alias.canvas = false;
    return config;
  }
};

export default nextConfig;
