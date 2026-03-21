import { createRequire } from "node:module";
import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const require = createRequire(import.meta.url);
const withNextIntl = createNextIntlPlugin();

const nextConfig: NextConfig = {
  turbopack: {
    resolveAlias: {
      tailwindcss: require.resolve("tailwindcss"),
    },
  },
};

export default withNextIntl(nextConfig);
