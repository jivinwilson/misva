import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "MISEVA",
    short_name: "MISEVA",
    description: "Everything in Place. Every Service in Control.",
    start_url: "/",
    display: "standalone",
    background_color: "#080F14",
    theme_color: "#080F14",
  };
}
