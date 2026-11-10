import { MessageCircle } from "lucide-react";
import { cn } from "@/lib/utils";

/** Marca do Bizu: um balão de conversa num quadrado em zinc. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground", className)}>
      <MessageCircle className="size-1/2" aria-hidden />
    </span>
  );
}
