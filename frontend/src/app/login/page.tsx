"use client";

import Script from "next/script";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { TriangleAlert } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Logo } from "@/components/painel/logo";
import { entrarComGoogle } from "@/lib/api";
import { lerSessao } from "@/lib/sessao";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: { client_id: string; callback: (r: { credential: string }) => void }): void;
          renderButton(elemento: HTMLElement, opcoes: Record<string, unknown>): void;
        };
      };
    };
  }
}

const CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";

const MENSAGENS: Record<number, string> = {
  403: "Esta conta Google não tem acesso ao Bizu.",
  0: "Não consegui falar com o servidor. Confira a conexão e tente de novo.",
};

export default function Login() {
  const router = useRouter();
  const botao = useRef<HTMLDivElement>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (lerSessao()) router.replace("/conversas");
  }, [router]);

  const iniciar = useCallback(() => {
    if (!window.google || !botao.current || !CLIENT_ID) return;
    window.google.accounts.id.initialize({
      client_id: CLIENT_ID,
      callback: async ({ credential }) => {
        setErro(null);
        const resultado = await entrarComGoogle(credential);
        if (resultado.ok) router.replace("/conversas");
        else setErro(MENSAGENS[resultado.status] ?? "Não foi possível entrar. Tente de novo.");
      },
    });
    botao.current.innerHTML = "";
    window.google.accounts.id.renderButton(botao.current, {
      theme: "outline",
      size: "large",
      text: "signin_with",
      locale: "pt-BR",
      width: 280,
    });
  }, [router]);

  return (
    <main className="grid min-h-dvh place-items-center p-4">
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onReady={iniciar} />
      <Card className="w-full max-w-sm">
        <CardHeader className="justify-items-center text-center">
          <Logo className="mb-2 size-10" />
          <CardTitle className="text-xl">Bizu</CardTitle>
          <CardDescription>Seu atendimento no WhatsApp, no seu jeito.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          {CLIENT_ID ? (
            <div ref={botao} className="min-h-10" />
          ) : (
            <Alert variant="destructive">
              <TriangleAlert />
              <AlertTitle>Login não configurado</AlertTitle>
              <AlertDescription>Defina NEXT_PUBLIC_GOOGLE_CLIENT_ID no arquivo .env.local.</AlertDescription>
            </Alert>
          )}
          {erro && (
            <Alert variant="destructive">
              <TriangleAlert />
              <AlertDescription>{erro}</AlertDescription>
            </Alert>
          )}
          <p className="text-center text-xs text-muted-foreground">
            Só a conta do corretor cadastrado consegue entrar.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
