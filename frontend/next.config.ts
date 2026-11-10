import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Painel 100% do lado do cliente (sessão no navegador): sem cacheComponents nem prefetch parcial,
  // que só trazem avisos de "navegação instantânea" para telas que dependem da sessão.
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
